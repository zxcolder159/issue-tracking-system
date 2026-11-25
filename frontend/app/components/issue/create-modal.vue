<script setup lang="ts">
import type { Form, FormErrorEvent, FormSubmitEvent } from '@nuxt/ui'
import type { ShallowUnwrapRef } from 'vue'
import type { CreateIssueForm, CreateIssueInput, Issue } from '~/types/issue'
import { issueTypes, priorities } from '~/constants/issue'
import { createIssueFormSchema } from '~/schemas/issue'
import { demoProject, demoMembers } from '~/data/demo-project'

const props = defineProps<{ create: (input: CreateIssueInput) => Promise<Issue> }>()
const open = defineModel<boolean>('open', { required: true })
const issueForm = useTemplateRef<ShallowUnwrapRef<Form<typeof createIssueFormSchema>>>('issueForm')
const saving = computed(() => issueForm.value?.loading ?? false)
const saveError = ref('')
const emptyForm = (): CreateIssueForm => ({
  projectId: demoProject.id,
  title: '',
  description: '',
  type: undefined,
  priority: undefined,
  startDate: '',
  dueDate: '',
  assigneeUserId: null,
})
const form = reactive(emptyForm())
const typeOptions = [...issueTypes]
const priorityOptions = [...priorities]
const assigneeOptions = [
  { label: 'Unassigned', value: 0 },
  ...demoMembers.map(member => ({ label: member.name, value: member.id })),
]
const assigneeValue = computed({
  get: () => form.assigneeUserId ?? 0,
  set: (value: number) => { form.assigneeUserId = value || null },
})

watch(open, value => {
  if (!value) return
  Object.assign(form, emptyForm())
  saveError.value = ''
  issueForm.value?.clear()
})

function handleError(event: FormErrorEvent) {
  const id = event.errors[0]?.id
  if (!id) return
  watch(saving, () => document.getElementById(id)?.focus(), { once: true, flush: 'post' })
}

async function handleSubmit(event: FormSubmitEvent<CreateIssueInput>) {
  saveError.value = ''
  try {
    await props.create({
      ...event.data,
      startDate: new Date(`${event.data.startDate}T00:00:00`).toISOString(),
      dueDate: new Date(`${event.data.dueDate}T00:00:00`).toISOString(),
    })
    open.value = false
  } catch {
    saveError.value = 'Could not save the issue. Your form is preserved — please try again.'
  }
}
</script>

<template lang="pug">
u-modal(
  v-model:open="open"
  title="Create issue"
  description="New issue will be added to To Do."
  :dismissible="!saving"
  :close="!saving"
  :ui="{ content: 'sm:max-w-3xl', footer: 'justify-end' }"
)
  template(#body)
    u-form#create-issue(
      ref="issueForm"
      class="space-y-6"
      :state="form"
      :schema="createIssueFormSchema"
      @submit="handleSubmit"
      @error="handleError"
    )
      u-form-field(
        label="Title"
        name="title"
        required
      )
        u-input#title(
          class="w-full"
          v-model="form.title"
          autofocus
          placeholder="Issue title"
        )
      div(class="grid gap-6 md:grid-cols-[minmax(0,1fr)_240px]")
        u-form-field(
          label="Description"
          name="description"
        )
          u-textarea#description(
            class="w-full"
            v-model="form.description"
            :rows="14"
            placeholder="Add a description…"
            :ui="{ base: 'resize-y' }"
          )
        div(class="space-y-4")
          u-form-field(
            label="Assignee"
            name="assigneeUserId"
          )
            u-select#assignee(
              class="w-full"
              v-model="assigneeValue"
              :items="assigneeOptions"
            )
          u-form-field(
            label="Type"
            name="type"
            required
          )
            u-select#type(
              class="w-full"
              v-model="form.type"
              :items="typeOptions"
              placeholder="Select type"
            )
          u-form-field(
            label="Priority"
            name="priority"
            required
          )
            u-select#priority(
              class="w-full"
              v-model="form.priority"
              :items="priorityOptions"
              placeholder="Select priority"
            )
          u-form-field(
            label="Start date"
            name="startDate"
            required
          )
            u-input#start(
              class="w-full"
              v-model="form.startDate"
              type="date"
            )
          u-form-field(
            label="Due date"
            name="dueDate"
            required
          )
            u-input#due(
              class="w-full"
              v-model="form.dueDate"
              type="date"
              :min="form.startDate || undefined"
            )
      u-alert(
        v-if="saveError"
        :title="saveError"
        color="error"
        variant="subtle"
        role="alert"
      )
  template(#footer)
    u-button(
      color="neutral"
      variant="outline"
      :disabled="saving"
      @click="open = false"
    ) Cancel
    u-button(
      type="submit"
      form="create-issue"
      :loading="saving"
    ) Save
</template>
