import { requestJson } from './http'

export interface Project {
  id: string
  projectType: string
  createdAt: string
  updatedAt: string
}

export function createProject(projectType: string): Promise<Project> {
  return requestJson('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ projectType }),
  })
}

export function getProject(id: string): Promise<Project> {
  return requestJson(`/api/projects/${encodeURIComponent(id)}`)
}
