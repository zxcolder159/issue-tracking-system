<script setup lang="ts">
import type { Issue } from '~/types/issue'
import { demoMembers } from '~/data/demo-project'

const props = defineProps<{ issue: Issue }>()
const assigneeName = computed(() => demoMembers.find(member => member.id === props.issue.assigneeUserId)?.name ?? 'Unassigned')
</script>

<template lang="pug">
u-card(
  class="issue-card"
  :ui="{ body: 'p-4 sm:p-4' }"
)
  h3(class="mb-3 break-words text-sm font-medium") {{ issue.title }}
  div(class="flex items-center gap-2")
    u-avatar(
      v-if="issue.assigneeUserId"
      :alt="assigneeName"
      size="2xs"
    )
    u-icon(
      class="text-dimmed"
      v-else
      name="i-lucide-user-round"
    )
    p(class="card-assignee text-xs text-muted") {{ assigneeName }}
</template>
