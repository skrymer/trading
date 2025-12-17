# Flyway Database Migration Setup

## Overview

Flyway has been integrated into the project for database schema version control and migrations. The current database schema has been baselined as version 1.

## Configuration

### Dependencies (build.gradle)

```kotlin
implementation 'org.flywaydb:flyway-core'
```

### Application Properties

```properties
# Flyway Migration Configuration
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.flyway.locations=classpath:db/migration
spring.flyway.out-of-order=false
spring.flyway.validate-on-migrate=true
```

## How It Works

1. **Baseline**: When Flyway first runs, it creates a baseline at version 1, marking the current database schema as the starting point.

2. **Migration Tracking**: Flyway creates a `flyway_schema_history` table to track which migrations have been applied.

3. **Future Migrations**: Any new migrations with version > 1 will be automatically applied when the application starts.

## Creating New Migrations

### Naming Convention

Migrations must follow this naming pattern:

```
V<VERSION>__<DESCRIPTION>.sql
```

Examples:
- `V2__Add_user_table.sql`
- `V3__Add_index_to_stocks.sql`
- `V4__Alter_portfolio_table.sql`

### Migration File Location

Place migration files in:
```
src/main/resources/db/migration/
```

### Example Migration

```sql
-- V2__Add_user_preferences_table.sql
CREATE TABLE user_preferences (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id VARCHAR(255) NOT NULL,
  theme VARCHAR(50) DEFAULT 'light',
  timezone VARCHAR(100) DEFAULT 'UTC',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
```

## Best Practices

1. **Never modify existing migrations** - Once a migration is applied, it should never be changed
2. **Always test migrations** - Test migrations locally before committing
3. **Use descriptive names** - Migration names should clearly describe what they do
4. **Keep migrations small** - Each migration should do one thing
5. **Use transactions** - Wrap DDL statements in transactions when possible (H2 supports this)
6. **Add indexes carefully** - Consider performance impact of adding indexes
7. **Document complex changes** - Add comments to explain non-obvious changes

## Verifying Migrations

### Check Migration Status

Query the Flyway schema history table:

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### View in H2 Console

1. Open http://localhost:8080/udgaard/h2-console
2. JDBC URL: `jdbc:h2:file:~/.trading-app/database/trading`
3. Username: `sa`
4. Password: (empty)
5. Query: `SELECT * FROM flyway_schema_history`

## Troubleshooting

### Migration Failed

If a migration fails:
1. Fix the migration SQL
2. Manually delete the failed entry from `flyway_schema_history`
3. Restart the application

### Out-of-Order Migrations

If you need to insert a migration between existing versions:
1. Set `spring.flyway.out-of-order=true` temporarily
2. Create the new migration with appropriate version number
3. Restart application
4. Set `spring.flyway.out-of-order=false` again

### Baseline Issues

If Flyway complains about baseline:
1. Check that `spring.flyway.baseline-on-migrate=true`
2. Verify `flyway_schema_history` table exists
3. Check that baseline version is correctly set

## Integration with jOOQ

After adding a migration:

1. **Apply migration** - Start the application to apply the migration
2. **Regenerate jOOQ code** - Run `./gradlew generateJooq`
3. **Update domain models** - Add/update domain models if schema changed
4. **Update repositories** - Update jOOQ repositories if needed
5. **Update mappers** - Update mappers for new fields
6. **Test changes** - Ensure all tests pass

## Current Status

- **Baseline Version**: 1 (current database schema)
- **Migrations Location**: `src/main/resources/db/migration/`
- **Test Migration**: V2__Add_flyway_test_comment.sql (verifies Flyway is working)

## Future Work

Consider adding:
- Database rollback scripts (Flyway Pro feature)
- Migration callbacks for complex data transformations
- Separate migration scripts for test data
- CI/CD integration for migration validation

---

_Last Updated: 2025-12-13_
_Flyway version: Managed by Spring Boot dependency management_
