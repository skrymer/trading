# Settings Page Implementation - API Credentials Management

## Date
2025-12-02

## Problem

The application failed to start in production because `secure.properties` file (containing API credentials) was not included in the installer. This is by design - we don't want to commit sensitive API keys to git or bundle them with the installer.

**Error on startup:**
```
Application failed to load properties from secure.properties
```

## Solution

Implemented a Settings page in the UI where users can enter their own API credentials, which are saved to `~/.trading-app/config.properties` on their local machine.

## Implementation

### 1. Backend Changes

#### SettingsController.kt
New REST controller with endpoints:
- `GET /api/settings/credentials` - Get current credentials (masked)
- `POST /api/settings/credentials` - Save credentials
- `GET /api/settings/credentials/status` - Check which credentials are configured

#### SettingsService.kt
Service that manages credential storage:
- Reads from `~/.trading-app/config.properties`
- Writes credentials to config file
- Creates template file on first run
- Updates system properties so changes are available immediately

#### ExternalConfigLoader.kt
Spring Boot ApplicationListener that:
- Loads config from `~/.trading-app/config.properties` before beans initialize
- Makes credentials available to `@Value` annotations
- Runs on application startup

#### spring.factories
Registers ExternalConfigLoader as an ApplicationListener

#### ApiCredentialsDto.kt
Data transfer object for API credentials:
```kotlin
data class ApiCredentialsDto(
    val ovtlyrToken: String,
    val ovtlyrUserId: String,
    val alphaVantageApiKey: String
)
```

### 2. Frontend Changes

#### pages/settings.vue
New settings page with:
- Form to enter Ovtlyr credentials (token, user ID)
- Form to enter Alpha Vantage API key
- Status badges showing which credentials are configured
- Help text with links to get API keys
- Save button to persist credentials

Features:
- Password-masked input fields
- Loading states during save
- Success/error toast notifications
- Configuration status indicators
- Instructions for obtaining credentials

#### layouts/default.vue
Added "Settings" link to navigation menu with gear icon

### 3. Configuration Changes

#### application.properties
Updated to use environment variables/system properties:
```properties
ovtlyr.cookies.token=${ovtlyr.cookies.token:}
ovtlyr.cookies.userid=${ovtlyr.cookies.userid:}
alphavantage.api.key=${alphavantage.api.key:}
```

The `:` after the property name provides a default empty value if not found.

#### secure.properties (Development Only)
Moved Alpha Vantage API key here for development:
- Contains Ovtlyr credentials
- Contains Alpha Vantage API key
- **Not committed to git** (in .gitignore)
- Used only for development
- Users configure via Settings page in production

## Configuration File Location

**Development:**
- `udgaard/src/main/resources/secure.properties` (gitignored)

**Production (User's Machine):**
- `~/.trading-app/config.properties`
- Created automatically on first run
- Editable via Settings UI or manually

## User Flow

### First Run:
1. User starts the application
2. Application creates `~/.trading-app/config.properties` with empty template
3. Application starts but API features won't work without credentials
4. User navigates to Settings page
5. User enters API credentials:
   - Ovtlyr token & user ID (from browser cookies)
   - Alpha Vantage API key (free from alphavantage.co)
6. User clicks Save
7. Credentials saved to `~/.trading-app/config.properties`
8. Application can now use API features

### Subsequent Runs:
1. Application loads credentials from `~/.trading-app/config.properties`
2. API features work immediately
3. User can update credentials anytime via Settings page

## Getting API Credentials

### Ovtlyr:
1. Visit https://console.ovtlyr.com
2. Login to your account
3. Open browser DevTools (F12)
4. Go to Application > Cookies
5. Copy `token` and `userid` cookie values
6. Paste into Settings page

### Alpha Vantage:
1. Visit https://www.alphavantage.co/support/#api-key
2. Enter email to get free API key
3. Copy API key from email
4. Paste into Settings page

**Free tier limits:**
- 5 API requests per minute
- 500 API requests per day

## Security

- Credentials stored locally in user's home directory
- Config file has standard file permissions (user read/write only)
- Credentials never sent to any server except the respective API providers
- Settings page masks credentials (password input fields)
- No credentials bundled in installer or committed to git

## Benefits

✅ **No sensitive data in git** - API keys never committed
✅ **User-specific credentials** - Each user uses their own API keys
✅ **Easy configuration** - Simple UI form, no manual file editing
✅ **Portable** - Works on Windows, macOS, Linux
✅ **Persistent** - Credentials saved between app restarts
✅ **Secure** - Credentials stay on user's machine

## Files Created

**Backend:**
- `src/main/kotlin/com/skrymer/udgaard/controller/SettingsController.kt`
- `src/main/kotlin/com/skrymer/udgaard/controller/dto/ApiCredentialsDto.kt`
- `src/main/kotlin/com/skrymer/udgaard/service/SettingsService.kt`
- `src/main/kotlin/com/skrymer/udgaard/config/ExternalConfigLoader.kt`
- `src/main/resources/META-INF/spring.factories`

**Frontend:**
- `app/pages/settings.vue`

**Modified:**
- `src/main/resources/application.properties` - Added env var placeholders
- `src/main/resources/secure.properties` - Moved AlphaVantage key here
- `app/layouts/default.vue` - Added Settings to navigation

## Testing

### Test Credentials Configuration:

1. **Start fresh (no config file):**
   ```bash
   rm -rf ~/.trading-app/config.properties
   npm run dev
   ```
   - App should start
   - Navigate to Settings
   - Should show "Not configured" status
   - Enter credentials and save
   - Should show "Configured" status

2. **Test with existing config:**
   ```bash
   # Config file exists
   npm run dev
   ```
   - App should load credentials
   - Settings page should show existing values (masked)
   - API features should work

3. **Test credential update:**
   - Change credentials in Settings
   - Click Save
   - Verify `~/.trading-app/config.properties` updated
   - Test API functionality

## Future Enhancements

### Option 1: Credential Validation
Add "Test Connection" buttons to verify credentials work:
```
POST /api/settings/credentials/test/ovtlyr
POST /api/settings/credentials/test/alphavantage
```

### Option 2: Import/Export
Allow users to export/import configuration:
```
GET /api/settings/export
POST /api/settings/import
```

### Option 3: Encrypted Storage
Encrypt credentials at rest using OS keychain:
- Windows: DPAPI
- macOS: Keychain
- Linux: Secret Service API

### Option 4: First-Run Wizard
Show setup wizard on first launch:
- Welcome screen
- API credentials configuration
- Sample data import
- Quick start guide

## Troubleshooting

### "Config file not found" warning on startup
**Normal behavior** - file is created automatically. User should configure via Settings page.

### "API calls failing" after configuration
1. Check Settings page shows credentials as "Configured"
2. Verify `~/.trading-app/config.properties` exists and has values
3. Test API keys independently:
   - Ovtlyr: Try logging into console.ovtlyr.com
   - Alpha Vantage: Test with curl/browser

### Changes not taking effect
Credentials are loaded on startup. After updating, either:
1. Restart the application, OR
2. The SettingsService updates system properties immediately on save

## References

- Spring Boot External Configuration: https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config
- Alpha Vantage API: https://www.alphavantage.co/documentation/
- Ovtlyr: https://console.ovtlyr.com
