import { issuesApi } from '~/services/issues'
import type { CreateIssueInput, Issue } from '~/types/issue'

export function useIssues(projectId: MaybeRefOrGetter<number>) {
  const key = computed(() => `project:${toValue(projectId)}:issues`)
  const { data: issues, status, error: requestError, refresh } = useAsyncData(
    key,
    () => issuesApi.list(toValue(projectId)),
    {
      server: false,
      default: (): Issue[] => [],
    },
  )
  const loading = computed(() => status.value === 'idle' || status.value === 'pending')
  const error = computed(() => requestError.value
    ? 'Could not load issues. Check browser storage access and try again.'
    : '')

  async function create(input: CreateIssueInput) {
    const issue = await issuesApi.create(input)
    if (issue.projectId === toValue(projectId)) {
      issues.value = [issue, ...issues.value]
    }
    return issue
  }

  return { issues, loading, error, refresh, create }
}
