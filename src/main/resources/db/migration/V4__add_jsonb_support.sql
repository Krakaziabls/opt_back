-- Добавляем расширение для поддержки JSONB
CREATE EXTENSION IF NOT EXISTS "hstore";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Добавляем колонки для планов запросов
ALTER TABLE sql_queries 
    ADD COLUMN original_plan TEXT,
    ADD COLUMN optimized_plan TEXT;

-- Обновляем колонки для использования JSONB
ALTER TABLE sql_queries 
    ALTER COLUMN original_plan TYPE jsonb USING original_plan::jsonb,
    ALTER COLUMN optimized_plan TYPE jsonb USING optimized_plan::jsonb; 