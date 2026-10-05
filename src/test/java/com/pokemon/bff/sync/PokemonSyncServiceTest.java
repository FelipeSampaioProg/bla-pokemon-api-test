package com.pokemon.bff.sync;

import com.pokemon.bff.dto.*;
import com.pokemon.bff.persistence.entity.PokemonEntity;
import com.pokemon.bff.persistence.entity.PokemonSkillEntity;
import com.pokemon.bff.persistence.entity.PokemonStatEntity;
import com.pokemon.bff.persistence.entity.PokemonMetadataEntity;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PokemonSyncServiceTest {

    @Mock
    private PokemonRepository pokemonRepository;

    @Mock
    private PokemonTombstoneRepository tombstoneRepository;

    @InjectMocks
    private PokemonSyncService syncService;

    private static PokemonSnapshot bulbasaur() {
        return new PokemonSnapshot(1, "bulbasaur", "img.png", 0.7, 6.9, "Seed Pokemon",
                List.of(new Skill("overgrow", "u-overgrow")),
                List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49)),
                "A strange seed was planted on its back.", true);
    }

    private PokemonEntity savedEntity() {
        ArgumentCaptor<PokemonEntity> captor = ArgumentCaptor.forClass(PokemonEntity.class);
        verify(pokemonRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldPersistEveryFieldOfANewPokemon() {
        when(pokemonRepository.findById(1)).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.sync(bulbasaur());

        PokemonEntity saved = savedEntity();
        assertEquals(1, saved.getId());
        assertEquals("bulbasaur", saved.getName());
        assertEquals("img.png", saved.getImageUrl());
        assertEquals(0.7, saved.getHeight());
        assertEquals(6.9, saved.getWeight());
        assertEquals("Seed Pokemon", saved.getCategory());
        assertEquals("A strange seed was planted on its back.", saved.getDescription());
        assertEquals(List.of("overgrow"), saved.getSkills().stream().map(PokemonSkillEntity::getName).toList());
        assertEquals(List.of("hp", "attack"), saved.getStats().stream().map(PokemonStatEntity::getName).toList());
        assertEquals(45, saved.getStats().getFirst().getValue());
        assertFalse(saved.isLocallyModified());
    }

    @Test
    void shouldReplaceStatsAndSkillsOfAnExistingPokemon() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", "old.png", 0.7, 6.9, "old desc", Instant.now());
        existing.getStats().add(new PokemonStatEntity(existing, "old-stat", 1));
        existing.getSkills().add(new PokemonSkillEntity(existing, "old-skill", null));
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.sync(bulbasaur());

        PokemonEntity saved = savedEntity();
        assertEquals("img.png", saved.getImageUrl());
        assertEquals("A strange seed was planted on its back.", saved.getDescription());
        assertEquals(List.of("overgrow"), saved.getSkills().stream().map(PokemonSkillEntity::getName).toList());
        assertEquals(2, saved.getStats().size());
    }

    @Test
    void shouldStoreTheSameDataFromTheListEntryAndFromTheDetailView() {
        var detail = new PokemonDetail(1, "bulbasaur", "bulbasaur", "img.png", 0.7, 6.9, "Seed Pokemon",
                List.of(new Skill("overgrow", "u-overgrow")),
                List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49)),
                "A strange seed was planted on its back.", true, new EvolutionNode("bulbasaur", List.of()), true);

        assertEquals(bulbasaur(), PokemonSnapshot.from(detail));
    }

    @Test
    void shouldKeepStoredCategoryAndDescriptionWhenSpeciesCouldNotBeLoaded() {
        PokemonEntity existing = new PokemonEntity(25, "pikachu", "old.png", 0.4, 6.0, "Stored description", Instant.now());
        existing.setCategory("Mouse Pokemon");
        when(pokemonRepository.findById(25)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.sync(new PokemonSnapshot(25, "pikachu", "new.png", 0.4, 6.0, null, List.of(),
                List.of(new PokemonStat("hp", 35)), null, false));

        assertEquals("Stored description", existing.getDescription());
        assertEquals("Mouse Pokemon", existing.getCategory());
        assertEquals("new.png", existing.getImageUrl());
        assertEquals(35, existing.getStats().getFirst().getValue());
    }

    @Test
    void shouldNotOverwriteLocallyModifiedPokemon() {
        PokemonEntity edited = new PokemonEntity(1, "my-bulbasaur", "custom.png", 0.6, 9.9, "edited", Instant.now());
        edited.setLocallyModified(true);
        edited.getSkills().add(new PokemonSkillEntity(edited, "custom-skill", null));
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(edited));

        syncService.sync(bulbasaur());

        verify(pokemonRepository, never()).save(any());
        assertEquals("my-bulbasaur", edited.getName());
        assertEquals("edited", edited.getDescription());
        assertEquals("custom-skill", edited.getSkills().getFirst().getName());
    }

    @Test
    void shouldAcceptSnapshotWithoutStatsOrSkills() {
        when(pokemonRepository.findById(132)).thenReturn(Optional.empty());
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.sync(new PokemonSnapshot(132, "ditto", null, 0.3, 4.0, null, null, null, null, true));

        PokemonEntity saved = savedEntity();
        assertTrue(saved.getStats().isEmpty());
        assertTrue(saved.getSkills().isEmpty());
    }

    @Test
    void shouldNotRecreateAPokemonDeletedThroughTheApi() {
        when(tombstoneRepository.existsById(1)).thenReturn(true);

        syncService.sync(bulbasaur());

        verify(pokemonRepository, never()).save(any());
    }

    @Test
    void shouldKeepProprietaryFieldsWhenRefreshingAReplicatedPokemon() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", "old.png", 0.7, 6.9, "old", Instant.now());
        var metadata = new PokemonMetadataEntity();
        metadata.setPokemon(existing);
        metadata.setRegion("Kanto");
        existing.setMetadata(metadata);
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));
        when(pokemonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.sync(bulbasaur());

        assertEquals("img.png", existing.getImageUrl());
        assertEquals("Kanto", existing.getMetadata().getRegion());
    }

    @Test
    void shouldReplicateNowAndReportWhetherItWasCreated() {
        when(pokemonRepository.findById(1)).thenReturn(Optional.empty());
        when(pokemonRepository.saveAndFlush(any())).thenAnswer(inv -> {
            PokemonEntity entity = inv.getArgument(0);
            entity.setVersion(0L);
            return entity;
        });

        var synced = syncService.syncNow(bulbasaur());
        var result = synced.body();

        assertEquals(PokemonSyncResult.Outcome.CREATED, result.outcome());
        assertTrue(synced.version().startsWith("0-"), synced.version());
        assertEquals("bulbasaur", result.pokemon().name());
        assertEquals(List.of(new PokemonStat("hp", 45), new PokemonStat("attack", 49)), result.pokemon().stats());
        verify(tombstoneRepository).deleteById(1);
    }

    @Test
    void shouldReportAnUpdateWhenThePokemonWasAlreadyReplicated() {
        PokemonEntity existing = new PokemonEntity(1, "bulbasaur", "old.png", 0.7, 6.9, "old", Instant.now());
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(existing));
        when(pokemonRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = syncService.syncNow(bulbasaur()).body();

        assertEquals(PokemonSyncResult.Outcome.UPDATED, result.outcome());
        assertEquals("img.png", result.pokemon().image());
    }

    /** The stored copy of {@link #bulbasaur()}, as a previous sync left it. */
    private static PokemonEntity storedBulbasaur() {
        PokemonEntity stored = new PokemonEntity(1, "bulbasaur", "img.png", 0.7, 6.9,
                "A strange seed was planted on its back.", Instant.parse("2026-10-01T00:00:00Z"));
        stored.setCategory("Seed Pokemon");
        stored.getSkills().add(new PokemonSkillEntity(stored, "overgrow", "u-overgrow"));
        stored.getStats().add(new PokemonStatEntity(stored, "hp", 45));
        stored.getStats().add(new PokemonStatEntity(stored, "attack", 49));
        return stored;
    }

    @Test
    void shouldNotRewriteACopyThatIsAlreadyUpToDate() {
        var stored = storedBulbasaur();
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(stored));

        stored.setVersion(7L);

        syncService.sync(bulbasaur());
        var synced = syncService.syncNow(bulbasaur());

        verify(pokemonRepository, never()).save(any());
        verify(pokemonRepository, never()).saveAndFlush(any());
        assertEquals(PokemonSyncResult.Outcome.UPDATED, synced.body().outcome());
        assertEquals("7-" + Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), synced.version());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), stored.getSyncedAt());
    }

    @Test
    void shouldTellAnUpToDateCopyFromAChangedOne() {
        var stored = storedBulbasaur();
        var snapshot = bulbasaur();

        assertTrue(PokemonSyncService.isUpToDate(snapshot, stored));
        // Without the species, category and description are not written, so they do not count.
        assertTrue(PokemonSyncService.isUpToDate(new PokemonSnapshot(1, "bulbasaur", "img.png", 0.7, 6.9, null,
                snapshot.skills(), snapshot.stats(), null, false), stored));
        assertFalse(PokemonSyncService.isUpToDate(new PokemonSnapshot(1, "bulbasaur", "img.png", 0.7, 6.9,
                "Seed Pokemon", snapshot.skills(), List.of(new PokemonStat("hp", 46), new PokemonStat("attack", 49)),
                snapshot.description(), true), stored));
        assertFalse(PokemonSyncService.isUpToDate(new PokemonSnapshot(1, "bulbasaur", "new.png", 0.7, 6.9,
                "Seed Pokemon", snapshot.skills(), snapshot.stats(), snapshot.description(), true), stored));
        assertFalse(PokemonSyncService.isUpToDate(new PokemonSnapshot(1, "bulbasaur", "img.png", 0.7, 6.9,
                "Seed Pokemon", List.of(), snapshot.stats(), snapshot.description(), true), stored));
        assertFalse(PokemonSyncService.isUpToDate(new PokemonSnapshot(1, "bulbasaur", "img.png", 0.7, 6.9,
                "Seed Pokemon", snapshot.skills(), snapshot.stats(), "Another text.", true), stored));
    }

    @Test
    void shouldKeepLocalChangesWhenReplicatingNow() {
        PokemonEntity edited = new PokemonEntity(1, "my-bulbasaur", "custom.png", 0.6, 9.9, "edited", Instant.now());
        edited.setLocallyModified(true);
        when(pokemonRepository.findById(1)).thenReturn(Optional.of(edited));

        var result = syncService.syncNow(bulbasaur()).body();

        assertEquals(PokemonSyncResult.Outcome.KEPT_LOCAL_CHANGES, result.outcome());
        assertEquals("custom.png", result.pokemon().image());
        verify(pokemonRepository, never()).save(any());
        verify(pokemonRepository, never()).saveAndFlush(any());
    }
}
