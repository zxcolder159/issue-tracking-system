import * as z from 'zod'
import { issueTypes, priorities, statuses } from '~/constants/issue'

const id = z.number().int().positive()

const issueFields = z.object({
  projectId: id,
  title: z.string().trim().min(1, 'Enter an issue title'),
  description: z.string().trim(),
  type: z.enum(issueTypes, { error: 'Select a type' }),
  priority: z.enum(priorities, { error: 'Select a priority' }),
  startDate: z.iso.datetime({ offset: true, error: 'Choose a start date' }),
  dueDate: z.iso.datetime({ offset: true, error: 'Choose a due date' }),
  assigneeUserId: id.nullable(),
})

const validDateRange = (issue: { startDate: string; dueDate: string }) =>
  Date.parse(issue.dueDate) >= Date.parse(issue.startDate)

const dateRangeError = {
  path: ['dueDate'],
  message: 'Due date cannot be earlier than start date',
}

export const createIssueSchema = issueFields.refine(validDateRange, dateRangeError)

export const createIssueFormSchema = issueFields.extend({
  type: issueFields.shape.type.optional().pipe(issueFields.shape.type),
  priority: issueFields.shape.priority.optional().pipe(issueFields.shape.priority),
  startDate: z.iso.date({ error: 'Choose a start date' }),
  dueDate: z.iso.date({ error: 'Choose a due date' }),
}).refine(validDateRange, dateRangeError)

export const issueSchema = createIssueSchema.safeExtend({
  id,
  status: z.enum(statuses),
  authorUserId: id,
  createdAt: z.iso.datetime({ offset: true }),
})
