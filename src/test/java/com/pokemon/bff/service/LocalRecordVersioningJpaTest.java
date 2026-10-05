package com.pokemon.bff.service;

import com.pokemon.bff.client.PokemonClient;
import com.pokemon.bff.dto.PokemonCreateRequest;
import com.pokemon.bff.dto.PokemonStat;
import com.pokemon.bff.dto.Skill;
import com.pokemon.bff.dto.PokemonUpdateRequest;
import com.pokemon.bff.dto.Versioned;
import com.pokemon.bff.exception.PreconditionFailedException;
import com.pokemon.bff.persistence.repository.PokemonRepository;
import com.pokemon.bff.persistence.repository.PokemonTombstoneRepository;
import com.pokemon.bff.sync.PokemonSnapshot;
import com.pokemon.bff.sync.PokemonSyncService;
import com.pokemon.bff.sync.PokemonSyncThrottle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The ETag / If-Match protection against real JPA and Hibernate (H2 in MySQL mode), which mocks cannot show:
 * Hibernate only bumps the version when a column of the {@code pokemon} row changes, so without the forced increment
 * a write that only touched child rows (proprietary fields, stats) would keep the ETag, and a stale If-Match would
 * still be accepted.
 * <p>
 * The schema comes from the entities here, not from the MySQL migration.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:versioning;MODE=MySQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LocalRecordVersioningJpaTest {
    private static final int ID = 9001;

    @Autowired
    private PokemonRepository pokemonRepository;

    @Autowired
    private PokemonTombstoneRepository tombstoneRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private LocalPokemonService service;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        // The service is not a Spring bean here, so its @Transactional does not apply: each call runs in its own
        // transaction, as it would through the proxy.
        var pokeApi = new PokeApiCache(mock(PokemonClient.class));
        var syncService = mock(PokemonSyncService.class);
        var pokeApiPokemon = new PokeApiPokemonService(pokeApi, syncService,
                new PokemonSyncThrottle(new NoOpCacheManager()), pokemonRepository, mock(PokemonCatalogService.class),
                Runnable::run);
        service = new LocalPokemonService(pokeApiPokemon, pokeApi, syncService, pokemonRepository, tombstoneRepository);
        transaction = new TransactionTemplate(transactionManager);
        pokemonRepository.deleteAll();
        tombstoneRepository.deleteAll();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transaction.execute(status -> work.get());
    }

    private String create() {
        return service.create(new PokemonCreateRequest(ID, "localmon", null, 1.0, 12.5, "Created locally",
                List.of(new PokemonStat("hp", 50)), List.of(), null, null, null)).version();
    }

    private static PokemonUpdateRequest region(String region) {
        return new PokemonUpdateRequest(null, null, null, null, null, null, null, null, null, region, null);
    }

    /** The condition a client that read this version sends (If-Match, in HTTP terms). */
    private static VersionCondition at(String version) {
        return VersionCondition.oneOf(List.of(version));
    }

    private String currentEtag() {
        return inTransaction(() -> service.findLocal(String.valueOf(ID))).version();
    }

    @Test
    void shouldMoveToANewVersionWhenOnlyTheProprietaryFieldsChange() {
        String read = create();
        assertEquals(read, currentEtag());

        Versioned<?> updated = inTransaction(() -> service.update(ID, at(read), region("Kanto")));

        assertNotEquals(read, updated.version());
        assertEquals(updated.version(), currentEtag(), "the ETag answered by the write is the one a later read sees");
    }

    @Test
    void shouldMoveToANewVersionWhenOnlyTheStatsChange() {
        String read = create();

        Versioned<?> updated = inTransaction(() -> service.update(ID, at(read), new PokemonUpdateRequest(null, null,
                null, null, null, null, List.of(new PokemonStat("hp", 60)), null, null, null, null)));

        assertNotEquals(read, updated.version());
        assertEquals(updated.version(), currentEtag());
    }

    @Test
    void shouldRefuseTheSecondOfTwoClientsThatReadTheSameVersion() {
        String readByBoth = create();

        inTransaction(() -> service.update(ID, at(readByBoth), region("Kanto")));

        assertThrows(PreconditionFailedException.class,
                () -> inTransaction(() -> service.update(ID, at(readByBoth), region("Johto"))));
        assertThrows(PreconditionFailedException.class,
                () -> inTransaction(() -> { service.delete(ID, at(readByBoth)); return null; }));
        assertEquals("Kanto", inTransaction(() -> service.findLocal(String.valueOf(ID))).body().metadata().region());
    }

    @Test
    void shouldKeepTheETagWhenASyncBringsNothingNew() {
        // The real synchronisation, as the background sync and POST /{nameOrId}/sync run it.
        var sync = new PokemonSyncService(pokemonRepository, tombstoneRepository);
        var snapshot = new PokemonSnapshot(25, "pikachu", "https://img.example/pikachu.png", 0.4, 6.0,
                "Mouse Pokemon", List.of(new Skill("static", "https://pokeapi.co/api/v2/ability/9/")),
                List.of(new PokemonStat("hp", 35)), "Electric mouse.", true);
        String replicated = inTransaction(() -> sync.syncNow(snapshot)).version();
        String read = inTransaction(() -> service.findLocal("25")).version();
        assertEquals(replicated, read, "the ETag answered by /sync is the record's current one");

        inTransaction(() -> { sync.sync(snapshot); return null; });
        assertEquals(read, inTransaction(() -> sync.syncNow(snapshot)).version());
        assertEquals(read, inTransaction(() -> service.findLocal("25")).version(),
                "an unchanged PokeAPI read must not invalidate the If-Match of a client editing the record");

        inTransaction(() -> { sync.sync(new PokemonSnapshot(25, "pikachu", "https://img.example/pikachu-2.png", 0.4,
                6.0, "Mouse Pokemon", snapshot.skills(), snapshot.stats(), "Electric mouse.", true)); return null; });
        String changed = inTransaction(() -> service.findLocal("25")).version();
        assertNotEquals(read, changed, "a real PokeAPI change still moves the record to a new version");
        var refreshed = inTransaction(() -> sync.syncNow(new PokemonSnapshot(25, "pikachu",
                "https://img.example/pikachu-3.png", 0.4, 6.0, "Mouse Pokemon", snapshot.skills(), snapshot.stats(),
                "Electric mouse.", true)));
        assertEquals(refreshed.version(), inTransaction(() -> service.findLocal("25")).version(),
                "after an update, the ETag answered by /sync is the stored version, not the one before the flush");
    }

    @Test
    void shouldFailAWriteThatLostTheRaceAfterPassingIfMatch() {
        String read = create();
        var concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> inTransaction(() -> {
            var entity = pokemonRepository.findById(ID).orElseThrow();
            // Another request commits a change between this one's read and its write.
            concurrent.execute(status -> service.update(ID, at(read), region("Johto")));
            pokemonRepository.forceVersionIncrement(entity);
            return null;
        }));
        assertEquals("Johto", inTransaction(() -> service.findLocal(String.valueOf(ID))).body().metadata().region());
    }
}
