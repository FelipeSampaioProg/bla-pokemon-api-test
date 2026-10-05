-- Demonstration data, so a fresh installation has something to show offline (User Stories 01 to 04).
--
-- 1. Copies of well-known PokeAPI Pokemon, as the synchronisation stores them (locally_modified = 0), enriched with
--    proprietary fields (pokemon_metadata). Being replicated copies, the synchronisation keeps refreshing their
--    PokeAPI data when they are read, and never touches the proprietary fields.
-- 2. Pokemon created locally (locally_modified = 1, ids from 90001), which exist only in the local database.
--
-- Generated from PokeAPI with the same mapping as the application (decimetres -> m, hectograms -> kg, English
-- category and first English description). Safe to apply to a database that already holds some of these Pokemon:
-- existing rows are left as they are (INSERT IGNORE / WHERE NOT EXISTS).

-- 1. Replicated PokeAPI Pokemon with proprietary fields
-- bulbasaur (#1)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (1, 'bulbasaur', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1.png', 0.7, 6.9, 'A strange seed was planted on its back at birth. The plant sprouts and grows with this POKéMON.', 'Seed Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'hp', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'attack', 49 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'defense', 49 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'special-attack', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'special-defense', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 1, 'speed', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 1 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 1, 'overgrow', 'https://pokeapi.co/api/v2/ability/65/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 1 AND name = 'overgrow');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 1, 'chlorophyll', 'https://pokeapi.co/api/v2/ability/34/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 1 AND name = 'chlorophyll');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (1, 'フシギダネ', 'Kanto', 'STARTER');

-- charmander (#4)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (4, 'charmander', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/4.png', 0.6, 8.5, 'Obviously prefers hot places. When it rains, steam is said to spout from the tip of its tail.', 'Lizard Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'hp', 39 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'attack', 52 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'defense', 43 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'special-attack', 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'special-defense', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 4, 'speed', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 4 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 4, 'blaze', 'https://pokeapi.co/api/v2/ability/66/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 4 AND name = 'blaze');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 4, 'solar-power', 'https://pokeapi.co/api/v2/ability/94/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 4 AND name = 'solar-power');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (4, 'ヒトカゲ', 'Kanto', 'STARTER');

-- squirtle (#7)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (7, 'squirtle', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/7.png', 0.5, 9.0, 'After birth, its back swells and hardens into a shell. Powerfully sprays foam from its mouth.', 'Tiny Turtle Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'hp', 44 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'attack', 48 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'defense', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'special-attack', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'special-defense', 64 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 7, 'speed', 43 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 7 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 7, 'torrent', 'https://pokeapi.co/api/v2/ability/67/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 7 AND name = 'torrent');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 7, 'rain-dish', 'https://pokeapi.co/api/v2/ability/44/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 7 AND name = 'rain-dish');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (7, 'ゼニガメ', 'Kanto', 'STARTER');

-- pikachu (#25)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (25, 'pikachu', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/25.png', 0.4, 6.0, 'When several of these POKéMON gather, their electricity could build and cause lightning storms.', 'Mouse Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'hp', 35 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'attack', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'defense', 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'special-attack', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'special-defense', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 25, 'speed', 90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 25 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 25, 'static', 'https://pokeapi.co/api/v2/ability/9/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 25 AND name = 'static');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 25, 'lightning-rod', 'https://pokeapi.co/api/v2/ability/31/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 25 AND name = 'lightning-rod');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (25, 'ピカチュウ', 'Kanto', 'MASCOT');

-- eevee (#133)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (133, 'eevee', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/133.png', 0.3, 6.5, 'Its genetic code is irregular. It may mutate if it is exposed to radiation from element STONEs.', 'Evolution Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'hp', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'attack', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'defense', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'special-attack', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'special-defense', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 133, 'speed', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 133 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 133, 'run-away', 'https://pokeapi.co/api/v2/ability/50/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 133 AND name = 'run-away');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 133, 'adaptability', 'https://pokeapi.co/api/v2/ability/91/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 133 AND name = 'adaptability');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 133, 'anticipation', 'https://pokeapi.co/api/v2/ability/107/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 133 AND name = 'anticipation');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (133, 'イーブイ', 'Kanto', 'EVOLUTION-HUB');

-- mewtwo (#150)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (150, 'mewtwo', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/150.png', 2.0, 122.0, 'It was created by a scientist after years of horrific gene splicing and DNA engineering experiments.', 'Genetic Pokémon', '2026-01-01 00:00:00.000000', 0, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'hp', 106 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'attack', 110 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'defense', 90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'special-attack', 154 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'special-defense', 90 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 150, 'speed', 130 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 150 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 150, 'pressure', 'https://pokeapi.co/api/v2/ability/46/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 150 AND name = 'pressure');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 150, 'unnerve', 'https://pokeapi.co/api/v2/ability/127/' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 150 AND name = 'unnerve');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (150, 'ミュウツー', 'Kanto', 'LEGENDARY');

-- 2. Pokemon created locally (they exist only here)
-- voltmouse (#90001)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (90001, 'voltmouse', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png', 0.5, 4.2, 'A demo Pokemon created in the local database. It stores static electricity in its whiskers.', 'Spark Mouse Pokemon', '2026-01-01 00:00:00.000000', 1, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'hp', 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'attack', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'defense', 35 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'special-attack', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'special-defense', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90001, 'speed', 85 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90001 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90001, 'static', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90001 AND name = 'static');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90001, 'quick-feet', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90001 AND name = 'quick-feet');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (90001, 'Ratovolt', 'Demo Lab', 'CUSTOM');

-- embercub (#90002)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (90002, 'embercub', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png', 0.7, 12.0, 'A demo Pokemon created in the local database. Its fur glows when it is happy.', 'Ember Bear Pokemon', '2026-01-01 00:00:00.000000', 1, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'hp', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'attack', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'defense', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'special-attack', 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'special-defense', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90002, 'speed', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90002 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90002, 'blaze', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90002 AND name = 'blaze');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (90002, 'Ursabrasa', 'Demo Lab', 'CUSTOM');

-- tidalfin (#90003)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (90003, 'tidalfin', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png', 1.1, 23.5, 'A demo Pokemon created in the local database. It rides the tides from one reef to another.', 'Wave Fin Pokemon', '2026-01-01 00:00:00.000000', 1, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'hp', 70 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'attack', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'defense', 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'special-attack', 70 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'special-defense', 65 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90003, 'speed', 60 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90003 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90003, 'torrent', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90003 AND name = 'torrent');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90003, 'swift-swim', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90003 AND name = 'swift-swim');
INSERT IGNORE INTO pokemon_metadata (pokemon_id, localized_name, region, classification_tag)
VALUES (90003, 'Barbatana', 'Demo Lab', 'CUSTOM');

-- sproutling (#90004)
INSERT IGNORE INTO pokemon (id, name, image_url, height, weight, description, category, synced_at, locally_modified, version)
VALUES (90004, 'sproutling', 'https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/poke-ball.png', 0.3, 1.8, 'A demo Pokemon created in the local database. It naps in the sun to grow its single leaf.', 'Seedling Pokemon', '2026-01-01 00:00:00.000000', 1, 0);
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'hp', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'hp');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'attack', 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'defense', 45 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'special-attack', 50 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'special-attack');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'special-defense', 55 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'special-defense');
INSERT INTO pokemon_stat (pokemon_id, name, value) SELECT 90004, 'speed', 40 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_stat WHERE pokemon_id = 90004 AND name = 'speed');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90004, 'overgrow', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90004 AND name = 'overgrow');
INSERT INTO pokemon_skill (pokemon_id, name, url) SELECT 90004, 'chlorophyll', NULL FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM pokemon_skill WHERE pokemon_id = 90004 AND name = 'chlorophyll');
