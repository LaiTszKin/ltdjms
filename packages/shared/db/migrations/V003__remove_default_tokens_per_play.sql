-- V003__remove_default_tokens_per_play.sql
-- Remove the default_tokens_per_play column from dice game configuration tables.
-- The tokens parameter is now required when playing dice games (no default value).
-- Dice count is determined dynamically: dice-game-1 uses 1 token = 1 dice,
-- dice-game-2 uses 1 token = 3 dice.

-- Drop the default_in_range constraint first (it references default_tokens_per_play)
ALTER TABLE dice_game1_config
    DROP CONSTRAINT IF EXISTS dice_game1_default_in_range;

ALTER TABLE dice_game2_config
    DROP CONSTRAINT IF EXISTS dice_game2_default_in_range;

-- Drop the non-negative constraint for default_tokens_per_play
ALTER TABLE dice_game1_config
    DROP CONSTRAINT IF EXISTS dice_game1_default_tokens_non_negative;

ALTER TABLE dice_game2_config
    DROP CONSTRAINT IF EXISTS dice_game2_default_tokens_non_negative;

-- Drop the default_tokens_per_play column from both tables
ALTER TABLE dice_game1_config
    DROP COLUMN IF EXISTS default_tokens_per_play;

ALTER TABLE dice_game2_config
    DROP COLUMN IF EXISTS default_tokens_per_play;
