<script setup lang="ts">

useHead({ title: 'Board · Issue Tracking System' })
const projectId = Number(useRuntimeConfig().public.projectId)
const { issues, members, statuses, loading, error, refresh, create } = useIssues(projectId)
const createOpen = ref(false)
</script>

<template lang="pug">
main(class="flex h-dvh min-w-0 flex-col gap-4 p-4 sm:p-6")
  div(class="flex shrink-0 justify-end")
    u-button(
      icon="i-lucide-plus"
      :disabled="loading || !!error"
      @click="createOpen = true"
    ) Create issue
  u-alert(
    v-if="error"
    :title="error"
    color="error"
    variant="subtle"
    :actions="[{ label: 'Retry', onClick: () => refresh() }]"
  )
  issue-board(
    :issues="issues"
    :statuses="statuses"
    :loading="loading"
  )
  issue-create-modal(
    v-model:open="createOpen"
    :create="create"
    :project-id="projectId"
    :members="members"
  )
</template>
