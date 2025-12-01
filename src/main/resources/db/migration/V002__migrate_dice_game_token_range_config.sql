-- V002__migrate_dice_game_token_range_config.sql
-- Migrate legacy dice_game1_config and dice_game2_config tables from a single
-- tokens_per_play column to the new min/max/default token range columns and
-- associated reward/multiplier configuration.

-- Ensure new columns exist for dice_game1_config
ALTER TABLE dice_game1_config
    ADD COLUMN IF NOT EXISTS min_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS max_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS default_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS reward_per_dice_value BIGINT;

-- Ensure new columns exist for dice_game2_config
ALTER TABLE dice_game2_config
    ADD COLUMN IF NOT EXISTS min_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS max_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS default_tokens_per_play BIGINT,
    ADD COLUMN IF NOT EXISTS straight_multiplier BIGINT,
    ADD COLUMN IF NOT EXISTS base_multiplier BIGINT,
    ADD COLUMN IF NOT EXISTS triple_low_bonus BIGINT,
    ADD COLUMN IF NOT EXISTS triple_high_bonus BIGINT;

-- Populate new columns based on legacy data (if present)
DO $$
BEGIN
    -- dice_game1_config: legacy schema has tokens_per_play column
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND column_name = 'tokens_per_play'
    ) THEN
        -- For legacy rows, keep previous behavior:
        --   min_tokens_per_play = 1
        --   max_tokens_per_play = tokens_per_play (or 1 if tokens_per_play < 1)
        --   default_tokens_per_play = tokens_per_play (or 1 if tokens_per_play < 1)
        --   reward_per_dice_value = 250000
        UPDATE dice_game1_config
        SET min_tokens_per_play = COALESCE(min_tokens_per_play, 1),
            max_tokens_per_play = COALESCE(max_tokens_per_play, GREATEST(tokens_per_play, 1)),
            default_tokens_per_play = COALESCE(default_tokens_per_play, GREATEST(tokens_per_play, 1)),
            reward_per_dice_value = COALESCE(reward_per_dice_value, 250000);

        -- Drop legacy constraint and column if they still exist
        ALTER TABLE dice_game1_config
            DROP CONSTRAINT IF EXISTS tokens_per_play_non_negative,
            DROP COLUMN IF EXISTS tokens_per_play;
    ELSE
        -- Fresh installs (no legacy column): ensure any existing rows have defaults
        UPDATE dice_game1_config
        SET min_tokens_per_play = COALESCE(min_tokens_per_play, 1),
            max_tokens_per_play = COALESCE(max_tokens_per_play, 10),
            default_tokens_per_play = COALESCE(default_tokens_per_play, 1),
            reward_per_dice_value = COALESCE(reward_per_dice_value, 250000);
    END IF;

    -- dice_game2_config: legacy schema has tokens_per_play column
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND column_name = 'tokens_per_play'
    ) THEN
        -- For legacy rows, keep previous behavior:
        --   min_tokens_per_play = 5
        --   max_tokens_per_play = tokens_per_play (or 5 if tokens_per_play < 5)
        --   default_tokens_per_play = tokens_per_play (or 5 if tokens_per_play < 5)
        --   straight_multiplier = 100000
        --   base_multiplier = 20000
        --   triple_low_bonus = 1500000
        --   triple_high_bonus = 2500000
        UPDATE dice_game2_config
        SET min_tokens_per_play = COALESCE(min_tokens_per_play, 5),
            max_tokens_per_play = COALESCE(max_tokens_per_play, GREATEST(tokens_per_play, 5)),
            default_tokens_per_play = COALESCE(default_tokens_per_play, GREATEST(tokens_per_play, 5)),
            straight_multiplier = COALESCE(straight_multiplier, 100000),
            base_multiplier = COALESCE(base_multiplier, 20000),
            triple_low_bonus = COALESCE(triple_low_bonus, 1500000),
            triple_high_bonus = COALESCE(triple_high_bonus, 2500000);

        ALTER TABLE dice_game2_config
            DROP CONSTRAINT IF EXISTS dice_game2_tokens_per_play_non_negative,
            DROP COLUMN IF EXISTS tokens_per_play;
    ELSE
        -- Fresh installs: ensure defaults are set for any rows
        UPDATE dice_game2_config
        SET min_tokens_per_play = COALESCE(min_tokens_per_play, 5),
            max_tokens_per_play = COALESCE(max_tokens_per_play, 50),
            default_tokens_per_play = COALESCE(default_tokens_per_play, 5),
            straight_multiplier = COALESCE(straight_multiplier, 100000),
            base_multiplier = COALESCE(base_multiplier, 20000),
            triple_low_bonus = COALESCE(triple_low_bonus, 1500000),
            triple_high_bonus = COALESCE(triple_high_bonus, 2500000);
    END IF;
END $$;

-- Enforce NOT NULL and default values for dice_game1_config
ALTER TABLE dice_game1_config
    ALTER COLUMN min_tokens_per_play SET NOT NULL,
    ALTER COLUMN min_tokens_per_play SET DEFAULT 1,
    ALTER COLUMN max_tokens_per_play SET NOT NULL,
    ALTER COLUMN max_tokens_per_play SET DEFAULT 10,
    ALTER COLUMN default_tokens_per_play SET NOT NULL,
    ALTER COLUMN default_tokens_per_play SET DEFAULT 1,
    ALTER COLUMN reward_per_dice_value SET NOT NULL,
    ALTER COLUMN reward_per_dice_value SET DEFAULT 250000;

-- Enforce NOT NULL and default values for dice_game2_config
ALTER TABLE dice_game2_config
    ALTER COLUMN min_tokens_per_play SET NOT NULL,
    ALTER COLUMN min_tokens_per_play SET DEFAULT 5,
    ALTER COLUMN max_tokens_per_play SET NOT NULL,
    ALTER COLUMN max_tokens_per_play SET DEFAULT 50,
    ALTER COLUMN default_tokens_per_play SET NOT NULL,
    ALTER COLUMN default_tokens_per_play SET DEFAULT 5,
    ALTER COLUMN straight_multiplier SET NOT NULL,
    ALTER COLUMN straight_multiplier SET DEFAULT 100000,
    ALTER COLUMN base_multiplier SET NOT NULL,
    ALTER COLUMN base_multiplier SET DEFAULT 20000,
    ALTER COLUMN triple_low_bonus SET NOT NULL,
    ALTER COLUMN triple_low_bonus SET DEFAULT 1500000,
    ALTER COLUMN triple_high_bonus SET NOT NULL,
    ALTER COLUMN triple_high_bonus SET DEFAULT 2500000;

-- Add check constraints for dice_game1_config if missing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_min_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_min_tokens_non_negative CHECK (min_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_max_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_max_tokens_non_negative CHECK (max_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_default_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_default_tokens_non_negative CHECK (default_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_reward_non_negative'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_reward_non_negative CHECK (reward_per_dice_value >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_min_max_order'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game1_config'
          AND constraint_name = 'dice_game1_default_in_range'
    ) THEN
        ALTER TABLE dice_game1_config
            ADD CONSTRAINT dice_game1_default_in_range CHECK (
                default_tokens_per_play >= min_tokens_per_play
                AND default_tokens_per_play <= max_tokens_per_play
            );
    END IF;
END $$;

-- Add check constraints for dice_game2_config if missing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_min_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_min_tokens_non_negative CHECK (min_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_max_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_max_tokens_non_negative CHECK (max_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_default_tokens_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_default_tokens_non_negative CHECK (default_tokens_per_play >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_straight_multiplier_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_straight_multiplier_non_negative CHECK (straight_multiplier >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_base_multiplier_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_base_multiplier_non_negative CHECK (base_multiplier >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_triple_low_bonus_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_triple_low_bonus_non_negative CHECK (triple_low_bonus >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_triple_high_bonus_non_negative'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_triple_high_bonus_non_negative CHECK (triple_high_bonus >= 0);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_min_max_order'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_min_max_order CHECK (min_tokens_per_play <= max_tokens_per_play);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE table_schema = 'public'
          AND table_name = 'dice_game2_config'
          AND constraint_name = 'dice_game2_default_in_range'
    ) THEN
        ALTER TABLE dice_game2_config
            ADD CONSTRAINT dice_game2_default_in_range CHECK (
                default_tokens_per_play >= min_tokens_per_play
                AND default_tokens_per_play <= max_tokens_per_play
            );
    END IF;
END $$;

