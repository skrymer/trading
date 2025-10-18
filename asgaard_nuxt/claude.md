# Claude.md - Nuxt with NuxtUI Project Guide

## Project Overview

This is a Nuxt 4 application using NuxtUI components, designed as a dashboard/admin interface. The project uses TypeScript, ESLint for code quality, and follows Vue 3 composition API patterns.

## Tech Stack

- **Framework**: Nuxt 4.1.2
- **UI Library**: NuxtUI 4.0.1
- **Language**: TypeScript 5.9.3
- **Package Manager**: pnpm 10.18.0
- **Styling**: Tailwind CSS (via NuxtUI)
- **Icons**: @iconify-json/lucide, @iconify-json/simple-icons
- **Date Utilities**: date-fns 4.1.0
- **Validation**: Zod 4.1.11
- **State/Utils**: VueUse 13.9.0
- **Charts**: Unovis 1.6.1

## Project Structure

```
asgaard_nuxt/
├── app/                      # Nuxt app directory
│   ├── layouts/              # Layout components
│   │   └── default.vue       # Main layout with sidebar
│   ├── pages/                # File-based routing
│   │   ├── index.vue         # Home page
│   │   └── settings/         # Settings pages
│   ├── components/           # Vue components
│   │   ├── home/            # Home-related components
│   │   └── settings/        # Settings-related components
│   ├── app.vue              # Root component
│   └── error.vue            # Error page
├── assets/
│   └── css/
│       └── main.css         # Global styles
├── nuxt.config.ts           # Nuxt configuration
├── package.json             # Dependencies
└── tsconfig.json            # TypeScript config
```

## Development Commands

- `pnpm dev` - Start development server on http://localhost:3000
- `pnpm build` - Build for production
- `pnpm preview` - Preview production build locally
- `pnpm lint` - Run ESLint
- `pnpm typecheck` - Run TypeScript type checking

## Key Features & Patterns

### 1. NuxtUI Components

This project uses NuxtUI components extensively. Common components include:

- **Layout**: `UContainer`, `UDashboardLayout`, `UDashboardPanel`, `UDashboardSidebar`
- **Navigation**: `UVerticalNavigation`, `UBreadcrumb`, `UCommandPalette`
- **Forms**: `UInput`, `UButton`, `USelect`, `UCheckbox`, `URadio`, `UTextarea`
- **Data**: `UTable`, `UCard`, `UBadge`, `UAvatarGroup`
- **Feedback**: `UNotification`, `UAlert`, `UProgress`, `UModal`, `USlideover`
- **Icons**: Access via `<UIcon name="i-lucide-{icon-name}" />`

### 2. File-Based Routing

Pages are automatically routed based on file structure:
- `pages/index.vue` → `/`
- `pages/settings.vue` → `/settings`
- `pages/settings/members.vue` → `/settings/members`

### 3. Auto-Imports

Nuxt auto-imports:
- Vue APIs (`ref`, `computed`, `watch`, etc.)
- Nuxt composables (`useRoute`, `useRouter`, `useFetch`, `useAsyncData`, etc.)
- VueUse composables (e.g., `useLocalStorage`, `useDark`, etc.)
- Components from `components/` directory
- NuxtUI composables (`useToast`, `useModal`, `useSlideover`, etc.)

### 4. TypeScript

- Full TypeScript support with strict type checking
- Use `nuxt typecheck` to validate types
- Vue SFC `<script setup lang="ts">` syntax preferred

### 5. API Routes

- Create API endpoints in `server/api/` directory
- Supports CORS via route rules in `nuxt.config.ts`

### 6. Styling

- Tailwind CSS classes available throughout
- NuxtUI provides design tokens via `app.config.ts`
- Dark mode support built-in
- Custom CSS in `assets/css/main.css`

## Code Style Guidelines

### ESLint Configuration

```javascript
{
  stylistic: {
    commaDangle: 'never',  // No trailing commas
    braceStyle: '1tbs'     // One True Brace Style
  }
}
```

### Vue Component Structure

```vue
<script setup lang="ts">
// 1. Imports
import { ref } from 'vue'

// 2. Props & Emits
const props = defineProps<{
  title: string
}>()

const emit = defineEmits<{
  update: [value: string]
}>()

// 3. Composables
const route = useRoute()
const toast = useToast()

// 4. Reactive state
const isOpen = ref(false)

// 5. Computed values
const displayTitle = computed(() => props.title.toUpperCase())

// 6. Methods
function handleClick() {
  // implementation
}
</script>

<template>
  <!-- Template here -->
</template>

<style scoped>
/* Scoped styles if needed */
</style>
```

## Common Patterns

### Data Fetching

```typescript
// Using useFetch (auto-imports)
const { data, pending, error } = await useFetch('/api/data')

// Using useAsyncData with custom logic
const { data } = await useAsyncData('key', () => $fetch('/api/data'))
```

### Forms with Validation

```vue
<script setup lang="ts">
import { z } from 'zod'

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8)
})

type Schema = z.output<typeof schema>

const state = reactive<Partial<Schema>>({
  email: undefined,
  password: undefined
})

async function onSubmit() {
  const result = schema.safeParse(state)
  if (!result.success) {
    // Handle validation errors
    return
  }
  // Submit form
}
</script>

<template>
  <UForm :state="state" :schema="schema" @submit="onSubmit">
    <UFormGroup label="Email" name="email">
      <UInput v-model="state.email" />
    </UFormGroup>
    <UButton type="submit">Submit</UButton>
  </UForm>
</template>
```

### Toast Notifications

```typescript
const toast = useToast()

toast.add({
  title: 'Success',
  description: 'Operation completed successfully',
  color: 'green',
  icon: 'i-lucide-check-circle'
})
```

### Modal & Slideover

```typescript
const modal = useModal()
const slideover = useSlideover()

// Open modal
modal.open(ComponentName, {
  // props
})

// Open slideover
slideover.open(ComponentName, {
  // props
})
```

### Dark Mode

```typescript
const colorMode = useColorMode()

// Toggle
colorMode.preference = colorMode.preference === 'dark' ? 'light' : 'dark'

// Check current mode
const isDark = computed(() => colorMode.value === 'dark')
```

## Important Notes

### When Adding New Features:

1. **Check auto-imports**: Many APIs don't need explicit imports
2. **Use NuxtUI components**: Prefer `UButton` over custom buttons for consistency
3. **Follow file conventions**: Place files in correct directories for auto-discovery
4. **Type everything**: Leverage TypeScript for better DX and fewer bugs
5. **Use composables**: Extract reusable logic into composables in `composables/` directory

### API Development:

- Place API routes in `server/api/`
- Use Nitro's `defineEventHandler` for type-safe handlers
- Return JSON directly (auto-serialized)

### Performance:

- Use `useFetch` with proper keys for caching
- Lazy-load components: `const Component = defineAsyncComponent(() => import('./Component.vue'))`
- Consider `<ClientOnly>` for client-side only components
- Use `.client.vue` or `.server.vue` suffixes for specific rendering

### Testing:

- Run `pnpm typecheck` before committing
- Run `pnpm lint` to catch style issues
- Test in both light and dark modes

## Useful Links

- [Nuxt Documentation](https://nuxt.com/docs)
- [NuxtUI Documentation](https://ui.nuxt.com)
- [NuxtUI Pro Components](https://ui.nuxt.com/pro/getting-started)
- [Tailwind CSS](https://tailwindcss.com/docs)
- [VueUse Composables](https://vueuse.org/)
- [Iconify Icons](https://icon-sets.iconify.design/)

## Quick Reference

### Common Composables

```typescript
// Navigation
const route = useRoute()
const router = useRouter()

// Head management
useHead({ title: 'Page Title' })
useSeoMeta({ description: 'Page description' })

// NuxtUI
const toast = useToast()
const modal = useModal()
const slideover = useSlideover()
const colorMode = useColorMode()

// VueUse
const isDark = useDark()
const { copy } = useClipboard()
const { isSupported, vibrate } = useVibrate()
```

### Environment Variables

- Create `.env` file for local development
- Access via `useRuntimeConfig()` for public vars
- Prefix with `NUXT_PUBLIC_` for client-side access

### Build & Deployment

- Production builds go to `.output/` directory
- Supports multiple deployment platforms (Vercel, Netlify, etc.)
- See [Nuxt deployment docs](https://nuxt.com/docs/getting-started/deployment)
