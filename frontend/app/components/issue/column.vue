<script setup lang="ts">
import type { Issue } from '~/types/issue'

defineProps<{ title: string; icon: string; issues: Issue[] }>()
</script>

<template lang="pug">
section(
  class="column flex h-full w-64 shrink-0 flex-col rounded-lg bg-muted p-3 lg:min-w-0 lg:flex-1"
  :aria-label="title"
)
  div(class="mb-4 flex shrink-0 items-center justify-between gap-2")
    div(class="flex items-center gap-2")
      u-icon(
        class="text-muted"
        :name="icon"
      )
      h2(class="text-sm font-semibold") {{ title }}
      u-badge(
        color="neutral"
        variant="subtle"
        size="sm"
      ) {{ issues.length }}
  div(class="min-h-0 flex-1 space-y-3 overflow-y-auto")
    issue-card(
      v-for="issue in issues"
      :key="issue.id"
      :issue="issue"
    )
    p(
      class="py-5 text-center text-xs text-dimmed"
      v-if="!issues.length"
    ) No issues
</template>
