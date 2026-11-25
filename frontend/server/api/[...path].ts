export default defineEventHandler(event => {
  const { backend } = useRuntimeConfig(event)
  if (!backend.email || !backend.password) {
    throw createError({ statusCode: 503, statusMessage: 'Configure backend credentials in frontend/.env' })
  }
  const credentials = new TextEncoder().encode(`${backend.email}:${backend.password}`)
  const path = getRouterParam(event, 'path') ?? ''
  const query = getRequestURL(event).search
  return proxyRequest(event, `${backend.baseURL}/api/${path}${query}`, {
    headers: {
      authorization: `Basic ${btoa(String.fromCharCode(...credentials))}`,
      accept: 'application/json',
    },
  })
})
