import { statuses } from '~/constants/issue'
import { demoProject, demoMembers, demoUser } from '~/data/demo-project'
import type { CreateIssueInput, Issue } from '~/types/issue'
import { createIssueSchema, issueSchema } from '~/schemas/issue'

const storageKey = 'issue-tracker:demo-issues:v1'

function readIssues(): Issue[] {
  const raw = localStorage.getItem(storageKey)
  if (!raw) return []
  const legacyStatuses: Record<string, string> = {
    'К выполнению': 'To Do',
    'В работе': 'In Progress',
    'Готово': 'Done',
  }
  const parsed: unknown = JSON.parse(raw, (key, value) =>
    key === 'status' && typeof value === 'string' ? legacyStatuses[value] ?? value : value,
  )
  return issueSchema.array().parse(parsed)
}

export const issuesApi = {
  async list(projectId: number): Promise<Issue[]> {
    return readIssues().filter(issue => issue.projectId === projectId)
  },

  async create(values: CreateIssueInput): Promise<Issue> {
    const input = createIssueSchema.parse(values)

    if (input.projectId !== demoProject.id) throw new Error('Project not found')
    if (input.assigneeUserId !== null && !demoMembers.some(member => member.id === input.assigneeUserId)) {
      throw new Error('Select a project member')
    }

    const issues = readIssues()
    const issue: Issue = {
      ...input,
      id: Math.max(0, ...issues.map(item => item.id)) + 1,
      status: statuses[0],
      authorUserId: demoUser.id,
      createdAt: new Date().toISOString(),
    }
    localStorage.setItem(storageKey, JSON.stringify([issue, ...issues]))
    return issue
  },
}
