<script setup lang="ts">
const failed = new URLSearchParams(location.search).has('login')
</script>

<template>
  <div class="grid min-h-dvh place-items-center bg-muted px-6 dark:bg-default">
    <div class="w-full max-w-sm space-y-8 text-center">
      <div class="space-y-3">
        <img src="/icon.svg" alt="" class="mx-auto size-20 rounded-2xl shadow-lg ring-1 ring-default" />
        <h1 class="font-display text-4xl font-bold tracking-wide uppercase">
          <span class="text-primary">Conta</span> <span class="text-highlighted">Hockey</span>
        </h1>
        <p class="text-muted">Results, rivalries and trash talk for the office Stiga table.</p>
      </div>
      <UAlert
        v-if="failed"
        color="error"
        variant="subtle"
        icon="i-lucide-shield-alert"
        title="Sign-in failed"
        description="Use your @conta.no Google account."
      />
      <UButton to="/oauth/login/google" external block size="xl" color="neutral" variant="outline" class="bg-default">
        <svg viewBox="0 0 48 48" class="size-5" aria-hidden="true">
          <path fill="#EA4335" d="M24 9.5c3.5 0 6.6 1.2 9.1 3.6l6.8-6.8C35.8 2.4 30.3 0 24 0 14.6 0 6.6 5.4 2.7 13.3l7.9 6.1C12.5 13.6 17.8 9.5 24 9.5z" />
          <path fill="#4285F4" d="M46.1 24.6c0-1.6-.1-3.1-.4-4.6H24v9h12.4c-.5 2.9-2.2 5.3-4.6 6.9l7.4 5.8c4.3-4 6.9-9.9 6.9-17.1z" />
          <path fill="#FBBC05" d="M10.6 28.6c-.5-1.4-.8-3-.8-4.6s.3-3.2.8-4.6l-7.9-6.1C1 16.6 0 20.2 0 24s1 7.4 2.7 10.7l7.9-6.1z" />
          <path fill="#34A853" d="M24 48c6.5 0 11.9-2.1 15.9-5.8l-7.4-5.8c-2.1 1.4-4.8 2.3-8.5 2.3-6.2 0-11.5-4.1-13.4-9.8l-7.9 6.1C6.6 42.6 14.6 48 24 48z" />
        </svg>
        Sign in with Google
      </UButton>
      <p class="text-xs text-dimmed">Only @conta.no accounts</p>
    </div>
  </div>
</template>
