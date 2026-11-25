import * as z from 'zod'
import type { CreateIssueInput, Issue } from '~/types/issue'
import { createIssueSchema } from '~/schemas/issue'

const namedEntity = z.object({ id: z.number(), name: z.string() })
const membership = z.object({ user: namedEntity })
const issueResponse = z.object({
  id: z.number(),
  title: z.string(),
  description: z.string().nullable(),
  type: z.string(),
  priority: z.string(),
  startDate: z.string(),
  dueDate: z.string(),
  author: membership,
  assignee: membership.nullable(),
  project: namedEntity,
  status: namedEntity,
  createdAt: z.string(),
}).transform((issue): Issue => ({
  id: issue.id,
  title: issue.title,
  description: issue.description ?? '',
  type: issue.type,
  priority: issue.priority,
  startDate: issue.startDate,
  dueDate: issue.dueDate,
  projectId: issue.project.id,
  authorUserId: issue.author.user.id,
  assigneeUserId: issue.assignee?.user.id ?? null,
  assigneeName: issue.assignee?.user.name ?? null,
  status: issue.status.name,
  createdAt: issue.createdAt,
}))

export const issuesApi = {
  async list(projectId: number) {
    return issueResponse.array().parse(await $fetch(`/api/projects/${projectId}/issues`))
  },
  async assignees(projectId: number) {
    const response = await $fetch(`/api/projects/${projectId}/issues/assignees`)
    return membership.array().parse(response).map(member => member.user)
  },
  async statuses(projectId: number) {
    const response = await $fetch(`/api/projects/${projectId}/statuses`)
    return namedEntity.array().parse(response).map(status => status.name)
  },
  async create(values: CreateIssueInput) {
    const { projectId, ...body } = createIssueSchema.parse(values)
    const csrf = await $fetch<{ headerName: string; token: string }>('/api/csrf')
    const response = await $fetch(`/api/projects/${projectId}/issues`, {
      method: 'POST',
      headers: { [csrf.headerName]: csrf.token },
      body,
    })
    return issueResponse.parse(response)
  },
}
