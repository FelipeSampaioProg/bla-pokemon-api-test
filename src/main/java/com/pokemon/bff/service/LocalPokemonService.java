package com.pokemon.bff.service;

import com.pokemon.bff.client.PokeApiNotFoundException;
import com.pokemon.bff.dto.*;
import com.pokemon.bff.exception.InvalidFieldException;
import com.pokemon.bff.exception.PokemonDeletedException;
import com.pokemon.bff.exception.PreconditionFailedException;
import com.pokemon.bff.persistence.entity.PokemonEntity;
import com.pokemon.bff.persistence.entity.PokemonMetadataEntity;
import com.pokemon.bff.persistence.entity.PokemonSkillEntity;
import com.pokemon.bff.persistence.entity.PokemonStatEntity;
import com.pokemon.bff.persistence.entity.PokemonTombstoneEntity;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * Pokemon stored in the local database (resource {@code /api/v1/local-pokemons}): reads, creation, replacement,
 * partial update, deletion and on-demand replication from PokeAPI, with the field rules of
 * {@link PokemonWriteValidator} and the optimistic concurrency of {@link VersionCondition}. Reading PokeAPI is
 * {@link PokeApiPokemonService}'s business; this service only asks it for a Pokemon to replicate.
 */
@Service
public class LocalPokemonService {
    private final PokeApiPokemonService pokeApiPokemon;
    private final PokeApiCache pokeApi;
    private final PokemonSyncService syncService;
    private final PokemonRepository pokemonRepository;
    private final PokemonTombstoneRepository tombstoneRepository;

    public LocalPokemonService(PokeApiPokemonService pokeApiPokemon, PokeApiCache pokeApi,
            PokemonSyncService syncService, PokemonRepository pokemonRepository,
            PokemonTombstoneRepository tombstoneRepository) {
        this.pokeApiPokemon = pokeApiPokemon;
        this.pokeApi = pokeApi;
        this.syncService = syncService;
        this.pokemonRepository = pokemonRepository;
        this.tombstoneRepository = tombstoneRepository;
    }

    /** Pokemon enumeration backed by the local store (records created or synchronised through this API). */
    @Transactional(readOnly = true)
    public PokemonPage findLocalPage(int page, int size) {
        Pagination.offset(page, size);
        var result = pokemonRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        var items = result.getContent().stream().map(this::toItem).toList();
        return new PokemonPage(items, page, size, Math.toIntExact(result.getTotalElements()), result.getTotalPages());
    }

    /** Same as {@link #create}, without the version. */
    public PokemonDetail createPokemon(PokemonCreateRequest request) {
        return create(request).body();
    }

    /**
     * Creates a local record. Not transactional on purpose: the name check calls PokeAPI, and holding a database
     * connection during that HTTP call is avoidable here (the entity is new, so nothing is lazily loaded; the unique
     * id and name constraints still settle a concurrent creation with a 409).
     */
    public Versioned<PokemonDetail> create(PokemonCreateRequest request) {
        validateCreateRequest(request);

        if (pokemonRepository.existsById(request.id())) {
            throw new IllegalArgumentException("Pokemon with id " + request.id() + " already exists");
        }
        if (pokemonRepository.findByName(normalizeName(request.name())).isPresent()) {
            throw new IllegalArgumentException("Pokemon with name " + request.name() + " already exists");
        }
        ensureNameIsFreeInPokeApi(normalizeName(request.name()), request.id());
        ensureIdIsFreeInPokeApi(request.id());
        // Creating a record on purpose undoes an earlier DELETE of the same id.
        tombstoneRepository.deleteById(request.id());

        PokemonEntity entity = new PokemonEntity(request.id(), normalizeName(request.name()),
                blankToNull(request.image()), request.height(), request.mass(), blankToNull(request.description()),
                Instant.now());
        entity.setCategory(normalizeCategory(request.category()));
        entity.setLocallyModified(true);
        entity.setStats(new ArrayList<>());
        entity.setSkills(new ArrayList<>());

        request.stats().forEach(stat -> entity.getStats().add(new PokemonStatEntity(entity, normalizeName(stat.name()), stat.value())));
        request.skills().forEach(skill -> entity.getSkills().add(new PokemonSkillEntity(entity, normalizeName(skill.name()), blankToNull(skill.url()))));

        if (hasMetadata(request)) {
            PokemonMetadataEntity metadata = new PokemonMetadataEntity();
            metadata.setPokemon(entity);
            metadata.setLocalizedName(blankToNull(request.localizedName()));
            metadata.setRegion(blankToNull(request.region()));
            metadata.setClassificationTag(blankToNull(request.classificationTag()));
            entity.setMetadata(metadata);
        }

        var saved = pokemonRepository.save(entity);
        return new Versioned<>(PokemonDetail.local(saved), saved.versionTag());
    }

    /** Same as {@link #update}, with no version condition and without the version. */
    @Transactional
    public PokemonDetail updatePokemon(Integer id, PokemonUpdateRequest request) {
        return update(id, null, request).body();
    }

    /**
     * Partial update ({@code PATCH}). Changing only proprietary fields keeps the record replicated; changing PokeAPI
     * data takes it over. Runs in one transaction (stats and skills are lazy), so the PokeAPI calls it may need — a
     * rename check, or replicating a Pokemon enriched before it was ever read — happen inside it; both are rare.
     *
     * @param expected the version the client read, or {@code null} for no condition: when set, the update only
     *                 happens if the record is still at that version (otherwise {@link PreconditionFailedException})
     */
    @Transactional
    public Versioned<PokemonDetail> update(Integer id, VersionCondition expected, PokemonUpdateRequest request) {
        validateUpdateRequest(id, request);

        if (request.onlyProprietaryFields() && !pokemonRepository.existsById(id)) {
            // Enriching a Pokemon that was never replicated: bring it from PokeAPI first, so a single PATCH
            // is enough. A Pokemon PokeAPI does not know, or one deleted through the API, stays a 404.
            replicateToEnrich(id, expected);
        }

        PokemonEntity entity = pokemonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pokemon not found with id " + id));
        checkVersion(expected, entity);

        if (request.name() != null) {
            String normalized = normalizeName(request.name());
            if (!normalized.equalsIgnoreCase(entity.getName())) {
                if (pokemonRepository.findByName(normalized).isPresent()) {
                    throw new IllegalArgumentException("Pokemon with name " + request.name() + " already exists");
                }
                ensureNameIsFreeInPokeApi(normalized, id);
            }
            entity.setName(normalized);
        }

        // A blank text field clears it.
        if (request.image() != null) {
            entity.setImageUrl(blankToNull(request.image()));
        }
        if (request.height() != null) {
            entity.setHeight(request.height());
        }
        if (request.mass() != null) {
            entity.setWeight(request.mass());
        }
        if (request.description() != null) {
            entity.setDescription(blankToNull(request.description()));
        }
        if (request.category() != null) {
            entity.setCategory(normalizeCategory(request.category()));
        }
        if (request.stats() != null) {
            if (entity.getStats() == null) {
                entity.setStats(new ArrayList<>());
            }
            entity.getStats().clear();
            request.stats().forEach(stat -> entity.getStats().add(new PokemonStatEntity(entity, normalizeName(stat.name()), stat.value())));
        }
        if (request.skills() != null) {
            if (entity.getSkills() == null) {
                entity.setSkills(new ArrayList<>());
            }
            entity.getSkills().clear();
            request.skills().forEach(skill -> entity.getSkills().add(new PokemonSkillEntity(entity, normalizeName(skill.name()), blankToNull(skill.url()))));
        }

        if (request.localizedName() != null || request.region() != null || request.classificationTag() != null) {
            if (entity.getMetadata() == null) {
                PokemonMetadataEntity metadata = new PokemonMetadataEntity();
                metadata.setPokemon(entity);
                entity.setMetadata(metadata);
            }
            if (request.localizedName() != null) {
                entity.getMetadata().setLocalizedName(blankToNull(request.localizedName()));
            }
            if (request.region() != null) {
                entity.getMetadata().setRegion(blankToNull(request.region()));
            }
            if (request.classificationTag() != null) {
                entity.getMetadata().setClassificationTag(blankToNull(request.classificationTag()));
            }
        }

        // Only a change to PokeAPI data takes the record over; proprietary fields ride on top of the replication.
        if (request.touchesReplicatedFields()) {
            entity.setLocallyModified(true);
        }
        return saveNewVersion(entity);
    }

    /**
     * Replicates a Pokemon that is not in the local store so a proprietary-fields-only {@code PATCH} can enrich it.
     * Answers 404 when the record was deleted through the API (only {@code POST} or {@code /sync} undo a deletion,
     * an update does not) or when PokeAPI does not know the id either.
     */
    private void replicateToEnrich(int id, VersionCondition expected) {
        if (tombstoneRepository.existsById(id)) {
            throw new PokemonDeletedException("Pokemon " + id + " was deleted from the local store; create it "
                    + "with POST or replicate it with POST /" + id + "/sync before updating it");
        }
        if (expected != null) {
            // There is no version yet, so no version condition can be met.
            throw new PreconditionFailedException("Pokemon " + id + " is not in the local store yet, so no version "
                    + "of it can match; send the update unconditionally to replicate it from PokeAPI", null);
        }
        try {
            replicate(String.valueOf(id));
        } catch (PokeApiNotFoundException notInPokeApi) {
            throw new NoSuchElementException("Pokemon not found with id " + id + " in the local store nor in PokeAPI");
        }
    }

    /** Same as {@link #replace}, with no version condition and without the version. */
    @Transactional
    public PokemonDetail replacePokemon(Integer id, PokemonReplaceRequest request) {
        return replace(id, null, request).body();
    }

    /**
     * Replaces a local Pokemon with the given state ({@code PUT}): every field takes the value sent, and a field left
     * out is cleared. The record must exist; ids of local Pokemon are chosen on {@code POST}.
     *
     * @param expected the version the client read, or {@code null} (see {@link #update})
     */
    @Transactional
    public Versioned<PokemonDetail> replace(Integer id, VersionCondition expected, PokemonReplaceRequest request) {
        validateReplaceRequest(id, request);

        PokemonEntity entity = pokemonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pokemon not found with id " + id));
        checkVersion(expected, entity);

        String normalized = normalizeName(request.name());
        if (!normalized.equalsIgnoreCase(entity.getName())) {
            if (pokemonRepository.findByName(normalized).isPresent()) {
                throw new IllegalArgumentException("Pokemon with name " + request.name() + " already exists");
            }
            ensureNameIsFreeInPokeApi(normalized, id);
        }
        entity.setName(normalized);
        entity.setImageUrl(blankToNull(request.image()));
        entity.setHeight(request.height());
        entity.setWeight(request.mass());
        entity.setDescription(blankToNull(request.description()));
        entity.setCategory(normalizeCategory(request.category()));

        entity.getStats().clear();
        request.stats().forEach(stat -> entity.getStats().add(new PokemonStatEntity(entity, normalizeName(stat.name()), stat.value())));
        entity.getSkills().clear();
        request.skills().forEach(skill -> entity.getSkills().add(new PokemonSkillEntity(entity, normalizeName(skill.name()), blankToNull(skill.url()))));

        if (request.localizedName() == null && request.region() == null && request.classificationTag() == null) {
            entity.setMetadata(null);
        } else {
            if (entity.getMetadata() == null) {
                PokemonMetadataEntity metadata = new PokemonMetadataEntity();
                metadata.setPokemon(entity);
                entity.setMetadata(metadata);
            }
            entity.getMetadata().setLocalizedName(blankToNull(request.localizedName()));
            entity.getMetadata().setRegion(blankToNull(request.region()));
            entity.getMetadata().setClassificationTag(blankToNull(request.classificationTag()));
        }

        entity.setLocallyModified(true);
        entity.setSyncedAt(Instant.now());
        return saveNewVersion(entity);
    }

    /**
     * Saves an update and moves the record to a new version, even when only child rows changed (metadata, stats,
     * skills), so the version returned, and any version read before, tell the two states apart.
     */
    private Versioned<PokemonDetail> saveNewVersion(PokemonEntity entity) {
        var saved = pokemonRepository.save(entity);
        pokemonRepository.forceVersionIncrement(saved);
        return new Versioned<>(PokemonDetail.local(saved), saved.versionTag());
    }

    /** Optimistic concurrency: a write with a condition only proceeds on the version the client read. */
    private static void checkVersion(VersionCondition expected, PokemonEntity current) {
        if (expected != null && !expected.isSatisfiedBy(current.versionTag())) {
            throw new PreconditionFailedException("Pokemon " + current.getId() + " was changed since it was read; "
                    + "load it again and retry", current.versionTag());
        }
    }

    /** Same as {@link #delete}, with no version condition. */
    @Transactional
    public void deletePokemon(Integer id) {
        delete(id, null);
    }

    /** @param expected the version the client read, or {@code null}: when set, a changed record is not deleted */
    @Transactional
    public void delete(Integer id, VersionCondition expected) {
        PokemonWriteValidator.id(id);
        PokemonEntity entity = pokemonRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pokemon not found with id " + id));
        checkVersion(expected, entity);
        pokemonRepository.delete(entity);
        // Otherwise the next PokeAPI read would replicate it back (without its proprietary fields).
        tombstoneRepository.save(new PokemonTombstoneEntity(id, Instant.now()));
    }

    /**
     * Replicates a Pokemon from PokeAPI into the local store now ({@code POST /{nameOrId}/sync}), so proprietary
     * fields can be added to it. The PokeAPI calls are made before any transaction is opened.
     */
    public Versioned<PokemonSyncResult> synchronize(String pokemon) {
        String normalized = PokemonCacheKeys.pokemon(pokemon);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Pokemon identifier must not be blank");
        }
        return replicate(normalized);
    }

    private Versioned<PokemonSyncResult> replicate(String nameOrId) {
        var detail = pokeApiPokemon.fetchDetailFromApi(nameOrId);
        return syncService.syncNow(PokemonSnapshot.from(detail));
    }

    /**
     * Refuses a local name that belongs to another Pokemon in PokeAPI: the replicated copy of that Pokemon would
     * collide with it on the unique name and could never be stored. If PokeAPI cannot answer, the write fails
     * with the upstream error rather than skipping the check.
     */
    private void ensureNameIsFreeInPokeApi(String normalizedName, int id) {
        try {
            var upstream = pokeApi.pokemon(normalizedName, true);
            if (upstream != null && upstream.id() != id) {
                throw new IllegalArgumentException("Pokemon name " + normalizedName
                        + " belongs to PokeAPI Pokemon #" + upstream.id() + "; choose another name");
            }
        } catch (PokeApiNotFoundException notInPokeApi) {
            // free to use
        }
    }

    /**
     * Refuses to create a local record with the id of a PokeAPI Pokemon. That record would take the Pokemon's place:
     * the synchronisation would skip it for good (it is owned locally), and the PokeAPI detail, which merges the
     * proprietary fields by id, would show this record's. To add local data to a PokeAPI Pokemon, replicate it and
     * PATCH it. If PokeAPI cannot answer, the creation fails with the upstream error rather than skipping the check.
     */
    private void ensureIdIsFreeInPokeApi(int id) {
        try {
            var upstream = pokeApi.pokemon(String.valueOf(id), true);
            if (upstream != null) {
                throw new InvalidFieldException("id", "Pokemon id " + id + " belongs to PokeAPI Pokemon "
                        + upstream.name() + "; to add local data to it use POST " + id + "/sync and PATCH, or "
                        + "choose another id");
            }
        } catch (PokeApiNotFoundException notInPokeApi) {
            // free to use
        }
    }

    /**
     * Detail of a Pokemon stored in the local database, looked up by numeric id or by name. Local records have
     * no evolution data, so the lineage contains only the Pokemon itself.
     */
    @Transactional(readOnly = true)
    public PokemonDetail findLocalByNameOrId(String pokemon) {
        return findLocal(pokemon).body();
    }

    /** Same as {@link #findLocalByNameOrId}, with the record's version (the condition of later writes). */
    @Transactional(readOnly = true)
    public Versioned<PokemonDetail> findLocal(String pokemon) {
        String normalized = PokemonCacheKeys.pokemon(pokemon);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Pokemon identifier must not be blank");
        }
        var entity = (normalized.chars().allMatch(Character::isDigit)
                ? pokemonRepository.findById(parseId(normalized))
                : pokemonRepository.findByName(normalized))
                .orElseThrow(() -> new NoSuchElementException("Pokemon " + pokemon.trim() + " not found in the local store"));
        return new Versioned<>(PokemonDetail.local(entity), entity.versionTag());
    }

    private static int parseId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Pokemon id is out of range");
        }
    }

    private PokemonItem toItem(PokemonEntity entity) {
        var skills = entity.getSkills() == null ? List.<Skill>of() : entity.getSkills().stream()
                .map(skill -> new Skill(skill.getName(), skill.getUrl()))
                .toList();
        return new PokemonItem(entity.getId(), entity.getName(), entity.getImageUrl(), entity.getCategory(),
                entity.getWeight(), skills, true, PokemonMetadata.from(entity.getMetadata()));
    }

    private void validateCreateRequest(PokemonCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Pokemon payload must not be null");
        }
        PokemonWriteValidator.id(request.id());
        validateFields(request.name(), true, request.image(), request.height(), request.mass(),
                request.description(), request.category(), request.stats(), request.skills());
        PokemonWriteValidator.proprietaryFields(request.localizedName(), request.region(), request.classificationTag());
    }

    private void validateReplaceRequest(Integer id, PokemonReplaceRequest request) {
        PokemonWriteValidator.id(id);
        if (request == null) {
            throw new IllegalArgumentException("Pokemon payload must not be null");
        }
        PokemonWriteValidator.bodyIdMatchesPath(request.id(), id);
        validateFields(request.name(), true, request.image(), request.height(), request.mass(),
                request.description(), request.category(), request.stats(), request.skills());
        PokemonWriteValidator.proprietaryFields(request.localizedName(), request.region(), request.classificationTag());
    }

    private void validateUpdateRequest(Integer id, PokemonUpdateRequest request) {
        PokemonWriteValidator.id(id);
        if (request == null) {
            throw new IllegalArgumentException("Pokemon payload must not be null");
        }
        PokemonWriteValidator.bodyIdMatchesPath(request.id(), id);
        if (!request.touchesReplicatedFields() && request.localizedName() == null && request.region() == null
                && request.classificationTag() == null) {
            throw new IllegalArgumentException("At least one field must be provided");
        }
        validateFields(request.name(), false, request.image(), request.height(), request.mass(),
                request.description(), request.category(), request.stats(), request.skills());
        PokemonWriteValidator.proprietaryFields(request.localizedName(), request.region(), request.classificationTag());
    }

    private static void validateFields(String name, boolean nameRequired, String image, Double height, Double mass,
            String description, String category, List<PokemonStat> stats, List<Skill> skills) {
        PokemonWriteValidator.name(name, nameRequired);
        PokemonWriteValidator.image(image);
        PokemonWriteValidator.height(height);
        PokemonWriteValidator.mass(mass);
        PokemonWriteValidator.description(description);
        PokemonWriteValidator.category(category);
        PokemonWriteValidator.stats(stats);
        PokemonWriteValidator.skills(skills);
    }

    /** A blank text field is cleared (stored as null), so a form can remove a value. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeCategory(String category) {
        return blankToNull(category);
    }

    private boolean hasMetadata(PokemonCreateRequest request) {
        return request.localizedName() != null || request.region() != null || request.classificationTag() != null;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
