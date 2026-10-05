package com.pokemon.bff.sync;

import com.pokemon.bff.config.PokemonAsyncConfiguration;
import com.pokemon.bff.persistence.entity.PokemonEntity;
import com.pokemon.bff.persistence.entity.PokemonSkillEntity;
import com.pokemon.bff.persistence.entity.PokemonStatEntity;
import com.pokemon.bff.dto.PokemonDetail;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.dto.Versioned;
import com.pokemon.bff.dto.PokemonSyncResult;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Keeps a local copy of what is read from the PokeAPI. Runs asynchronously so reads never wait for the database;
 * failures are logged by the async exception handler and never reach the caller.
 */
@Service
public class PokemonSyncService {
    private static final Logger log = LoggerFactory.getLogger(PokemonSyncService.class);

    private final PokemonRepository pokemonRepository;
    private final PokemonTombstoneRepository tombstoneRepository;

    public PokemonSyncService(PokemonRepository pokemonRepository, PokemonTombstoneRepository tombstoneRepository) {
        this.pokemonRepository = pokemonRepository;
        this.tombstoneRepository = tombstoneRepository;
    }

    /**
     * Copies a PokeAPI read to the local store, in the background. The list entry and the detail view send the same
     * snapshot, so the stored record is complete whichever of them stored it. It never recreates a Pokemon deleted
     * through the API, never overwrites local changes to PokeAPI fields and never touches the proprietary fields.
     * <p>
     * A copy already up to date is not written: rewriting it would move the record to a new version (ETag) without
     * any change, and a client editing it would get a 412 for nothing.
     */
    @Async(PokemonAsyncConfiguration.POKEMON_SYNC_EXECUTOR)
    @Transactional
    public void sync(PokemonSnapshot snapshot) {
        if (tombstoneRepository.existsById(snapshot.id())) {
            log.debug("Skipping PokeAPI sync for Pokemon {}, deleted through the API", snapshot.id());
            return;
        }
        Optional<PokemonEntity> existing = pokemonRepository.findById(snapshot.id());
        if (isLocallyModified(existing)) {
            log.debug("Skipping PokeAPI sync for locally modified Pokemon {}", snapshot.id());
            return;
        }
        if (existing.isPresent() && isUpToDate(snapshot, existing.get())) {
            log.debug("Skipping PokeAPI sync for Pokemon {}, already up to date", snapshot.id());
            return;
        }
        pokemonRepository.save(apply(snapshot, existing.orElseGet(PokemonEntity::new)));
    }

    /**
     * Replicates a Pokemon now, at the user's request ({@code POST /{nameOrId}/sync}). Being explicit, it lifts a
     * deletion mark; local changes to PokeAPI fields still win. Returns the local record as it stands afterwards, with
     * its version (like every other write); a copy already up to date is not rewritten (outcome {@code UPDATED}: it is
     * current).
     */
    @Transactional
    public Versioned<PokemonSyncResult> syncNow(PokemonSnapshot snapshot) {
        tombstoneRepository.deleteById(snapshot.id());
        Optional<PokemonEntity> existing = pokemonRepository.findById(snapshot.id());
        if (isLocallyModified(existing)) {
            return result(PokemonSyncResult.Outcome.KEPT_LOCAL_CHANGES, existing.get());
        }
        if (existing.isPresent() && isUpToDate(snapshot, existing.get())) {
            return result(PokemonSyncResult.Outcome.UPDATED, existing.get());
        }
        var outcome = existing.isPresent() ? PokemonSyncResult.Outcome.UPDATED : PokemonSyncResult.Outcome.CREATED;
        // Flushed now so the version returned is the one the commit will store.
        var saved = pokemonRepository.saveAndFlush(apply(snapshot, existing.orElseGet(PokemonEntity::new)));
        return result(outcome, saved);
    }

    private static Versioned<PokemonSyncResult> result(PokemonSyncResult.Outcome outcome, PokemonEntity entity) {
        return new Versioned<>(new PokemonSyncResult(outcome, PokemonDetail.local(entity)), entity.versionTag());
    }

    /** Whether the stored copy already holds every PokeAPI field {@link #apply} would write. */
    static boolean isUpToDate(PokemonSnapshot snapshot, PokemonEntity entity) {
        if (!Objects.equals(snapshot.name(), entity.getName())
                || !Objects.equals(snapshot.image(), entity.getImageUrl())
                || !Objects.equals(snapshot.height(), entity.getHeight())
                || !Objects.equals(snapshot.mass(), entity.getWeight())) {
            return false;
        }
        if (snapshot.speciesAvailable() && (!Objects.equals(snapshot.category(), entity.getCategory())
                || !Objects.equals(snapshot.description(), entity.getDescription()))) {
            return false;
        }
        var storedSkills = entity.getSkills().stream().map(skill -> new Skill(skill.getName(), skill.getUrl())).toList();
        var storedStats = entity.getStats().stream().map(stat -> new PokemonStat(stat.getName(), stat.getValue())).toList();
        return snapshot.skills().equals(storedSkills) && snapshot.stats().equals(storedStats);
    }

    /** Writes the PokeAPI fields of the snapshot; the proprietary fields (metadata) are left as they are. */
    private static PokemonEntity apply(PokemonSnapshot snapshot, PokemonEntity entity) {
        entity.setId(snapshot.id());
        entity.setName(snapshot.name());
        entity.setImageUrl(snapshot.image());
        entity.setHeight(snapshot.height());
        entity.setWeight(snapshot.mass());
        if (snapshot.speciesAvailable()) {
            entity.setCategory(snapshot.category());
            entity.setDescription(snapshot.description());
        }
        entity.setSyncedAt(Instant.now());

        entity.getSkills().clear();
        snapshot.skills().forEach(skill ->
                entity.getSkills().add(new PokemonSkillEntity(entity, skill.name(), skill.url())));
        entity.getStats().clear();
        snapshot.stats().forEach(stat ->
                entity.getStats().add(new PokemonStatEntity(entity, stat.name(), stat.value())));
        return entity;
    }

    /** Records created or edited through the API are owned locally and must not be overwritten by the PokeAPI. */
    private static boolean isLocallyModified(Optional<PokemonEntity> existing) {
        return existing.map(PokemonEntity::isLocallyModified).orElse(false);
    }
}
