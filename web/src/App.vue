<script setup lang="ts">
import { onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AddMatchSheet from './components/AddMatchSheet.vue'
import ChatDock from './components/ChatDock.vue'
import MatchSheet from './components/MatchSheet.vue'
import PlayersSlideover from './components/PlayersSlideover.vue'
import ProfileSlideover from './components/ProfileSlideover.vue'
import SignIn from './components/SignIn.vue'
import { addMatchOpen, chatOpen, loaded, me, openMatchId, playersOpen, profileOpen, refresh, signedOut } from './store'

onMounted(refresh)

// Push notifications link to /?match=<id> or /?chat=1: open that, then drop the query
const route = useRoute()
const router = useRouter()
watch(
  () => route.query,
  ({ match, chat }) => {
    if (match) openMatchId.value = Number(match)
    if (chat) chatOpen.value = true
    if (match || chat) router.replace({ query: {} })
  },
  { immediate: true },
)

const tabs = [
  { to: '/', label: 'Home', icon: 'i-lucide-house' },
  { to: '/stats', label: 'Stats', icon: 'i-lucide-chart-column' },
  { to: '/rivals', label: 'Rivals', icon: 'i-lucide-swords' },
]
const tabClass = 'flex flex-col items-center gap-0.5 py-2 text-xs text-toned'
const activeTabClass = '!text-primary font-semibold'
</script>

<template>
  <UApp :toaster="{ position: 'top-center' }">
    <SignIn v-if="signedOut" />
    <div v-else-if="!loaded" class="min-h-dvh bg-muted dark:bg-default" />
    <div v-else class="min-h-dvh bg-muted pb-[calc(6rem+env(safe-area-inset-bottom))] sm:pb-10 dark:bg-default">
      <header class="sticky top-0 z-20 border-b border-default bg-default/80 pt-[env(safe-area-inset-top)] backdrop-blur">
        <div class="mx-auto flex h-14 max-w-6xl items-center gap-2 px-4">
          <RouterLink to="/" class="mr-auto flex items-center gap-2.5">
            <img src="/icon.svg" alt="" class="size-8 rounded-lg ring-1 ring-default" />
            <span class="font-display text-xl leading-none font-bold tracking-wide uppercase">
              <span class="text-primary">Conta</span> <span class="text-highlighted">Hockey</span>
            </span>
          </RouterLink>
          <nav class="hidden items-center gap-1 sm:flex">
            <UButton
              v-for="t in tabs"
              :key="t.to"
              :to="t.to"
              :icon="t.icon"
              :label="t.label"
              color="neutral"
              variant="ghost"
              active-color="primary"
              active-variant="soft"
            />
            <UButton icon="i-lucide-users" label="Players" color="neutral" variant="ghost" @click="playersOpen = true" />
          </nav>
          <UColorModeButton />
          <UButton class="hidden sm:inline-flex" icon="i-lucide-plus" label="New match" @click="addMatchOpen = true" />
          <UButton color="neutral" variant="ghost" class="rounded-full p-0.5" aria-label="Profile" @click="profileOpen = true">
            <UAvatar :src="me?.pictureUrl" :alt="me?.name ?? 'Profile'" size="sm" referrerpolicy="no-referrer" />
          </UButton>
        </div>
      </header>

      <main class="mx-auto max-w-6xl space-y-4 px-4 py-4 sm:py-6">
        <RouterView />
      </main>

      <!-- Mobile tab bar: the primary action sits in the centre, in thumb reach -->
      <nav class="fixed inset-x-0 bottom-0 z-20 border-t border-default bg-default/90 pb-[env(safe-area-inset-bottom)] backdrop-blur sm:hidden">
        <div class="grid grid-cols-5 items-end">
          <RouterLink v-for="t in tabs.slice(0, 2)" :key="t.to" :to="t.to" :class="tabClass" :exact-active-class="activeTabClass">
            <UIcon :name="t.icon" class="size-6" />{{ t.label }}
          </RouterLink>
          <div class="flex justify-center">
            <UButton
              icon="i-lucide-plus"
              size="xl"
              class="-mt-5 size-14 justify-center rounded-2xl shadow-lg ring-4 shadow-primary/30 ring-default"
              :ui="{ leadingIcon: 'size-7' }"
              aria-label="New match"
              @click="addMatchOpen = true"
            />
          </div>
          <RouterLink :to="tabs[2].to" :class="tabClass" :exact-active-class="activeTabClass">
            <UIcon :name="tabs[2].icon" class="size-6" />{{ tabs[2].label }}
          </RouterLink>
          <button type="button" :class="tabClass" @click="playersOpen = true">
            <UIcon name="i-lucide-users" class="size-6" />Players
          </button>
        </div>
      </nav>

      <ChatDock />
      <AddMatchSheet />
      <MatchSheet />
      <PlayersSlideover />
      <ProfileSlideover />
    </div>
  </UApp>
</template>
