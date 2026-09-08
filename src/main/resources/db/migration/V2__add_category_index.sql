-- Category filtering on the list endpoint (GET /api/v1/products?category=...).
--
-- The index is on lower(category) rather than on the bare column because the filter matches
-- case-insensitively, mirroring ux_products_name_lower and for the same reason: category is free
-- text typed by a caller, and "Kitchen" and "kitchen" are one category to a user. A plain index on
-- category would not be usable by a lower(category) = lower(?) predicate at all, so the functional
-- index is what actually makes the filter cheap rather than a sequential scan.
--
-- The pairing is exact and therefore fragile: the query has to say lower(), not upper(), or the
-- planner ignores this index without complaining. ProductRepository spells the predicate out for
-- that reason, and ProductSchemaTest holds the two together with an EXPLAIN.

CREATE INDEX ix_products_category_lower ON products (lower(category));

COMMENT ON INDEX ix_products_category_lower IS 'Supports case-insensitive filtering by category';