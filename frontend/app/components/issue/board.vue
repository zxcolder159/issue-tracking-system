<script setup lang="ts">
import { statuses } from '~/constants/issue'
import type { Issue } from '~/types/issue'

const props = defineProps<{ issues: Issue[]; loading: boolean }>()
const icons: Record<(typeof statuses)[number], string> = {
  'To Do': 'i-lucide-circle-dashed',
  'In Progress': 'i-lucide-circle-dot',
  'In Review': 'i-lucide-circle-ellipsis',
  'Done': 'i-lucide-circle-check',
}
const columns = computed(() => statuses.map(title => ({
  title, icon: icons[title], issues: props.issues.filter(issue => issue.status === title),
})))
</script>

<template lang="pug">
div(class="board flex min-h-0 min-w-0 flex-1 gap-4 overflow-x-auto overscroll-x-contain pb-2")
  template(v-if="loading")
    u-skeleton(
      class="h-full w-64 shrink-0"
      v-for="status in statuses"
      :key="status"
      role="status"
      aria-label="Loading issues"
    )
  template(v-else)
    issue-column(
      v-for="column in columns"
      :key="column.title"
      v-bind="column"
    )
</template>
