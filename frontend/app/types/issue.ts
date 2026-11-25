import type { z } from 'zod'
import type { createIssueFormSchema, createIssueSchema, issueSchema } from '~/schemas/issue'

export type CreateIssueForm = z.input<typeof createIssueFormSchema>
export type CreateIssueInput = z.input<typeof createIssueSchema>
export type Issue = z.output<typeof issueSchema>
