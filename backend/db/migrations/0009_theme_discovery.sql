-- Editorial classification; a theme can belong to several discovery categories.
CREATE TABLE IF NOT EXISTS theme_categories (
 entity_id text NOT NULL REFERENCES entities(id) ON DELETE CASCADE,
 category_id text NOT NULL CHECK(category_id IN ('with-god','emotions','relationships','character','daily-life','foundations','community','eternity')),
 PRIMARY KEY(entity_id,category_id)
);
CREATE INDEX IF NOT EXISTS theme_categories_category ON theme_categories(category_id,entity_id);
