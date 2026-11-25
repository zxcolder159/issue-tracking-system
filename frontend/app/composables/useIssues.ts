import { issuesApi } from '~/services/issues'
import type { CreateIssueInput, Issue } from '~/types/issue'
import type { Member } from '~/types/project'

export function useIssues(projectId: MaybeRefOrGetter<number>) {
  const key = computed(() => `project:${toValue(projectId)}:issues`)
  const { data, status, error: requestError, refresh } = useAsyncData(
    key,
    async () => {
      const id = toValue(projectId)
      const [issues, members, statuses] = await Promise.all([
        issuesApi.list(id), issuesApi.assignees(id), issuesApi.statuses(id),
      ])
      return { issues, members, statuses }
    },
    {
      server: false,
      default: (): { issues: Issue[]; members: Member[]; statuses: string[] } => ({
        issues: [], members: [], statuses: [],
      }),
    },
  )
  const loading = computed(() => status.value === 'idle' || status.value === 'pending')
  const error = computed(() => requestError.value
    ? 'Could not load the project. Check that the backend and database are running.'
    : '')

  async function create(input: CreateIssueInput) {
    const issue = await issuesApi.create(input)
    if (issue.projectId === toValue(projectId)) {
      data.value = { ...data.value, issues: [issue, ...data.value.issues] }
    }
    return issue
  }

  return {
    issues: computed(() => data.value.issues),
    members: computed(() => data.value.members),
    statuses: computed(() => data.value.statuses),
    loading, error, refresh, create,
  }
}
