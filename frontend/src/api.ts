import type {
  ApplicationDetail,
  ApplicationStatus,
  ApplicationSummary,
  CheckResult,
  CreateApplicationRequest,
  DocumentType,
  LoginResponse,
  Overview,
  Page,
  User,
} from './types';

const TOKEN_KEY = 'meridian.token';

// sessionStorage clears when the tab closes, which is a sensible default for an internal admin tool.
export const tokenStore = {
  get: () => sessionStorage.getItem(TOKEN_KEY),
  set: (token: string) => sessionStorage.setItem(TOKEN_KEY, token),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

/** Raised for any non-2xx response. details carries the server's list (e.g. failing checks). */
export class ApiError extends Error {
  status: number;
  details: string[];

  constructor(status: number, message: string, details: string[] = []) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

type UnauthorizedHandler = () => void;
let onUnauthorized: UnauthorizedHandler = () => {};
export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  onUnauthorized = handler;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) headers.set('Content-Type', 'application/json');
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let response: Response;
  try {
    response = await fetch(`/api${path}`, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Is the API running?');
  }

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    let details: string[] = [];
    try {
      const body = await response.json();
      message = body.message ?? message;
      details = body.details ?? [];
    } catch {
      // Not JSON. Keep the generic message.
    }
    // A 401 on a call that carried a token means the session ended. Login failures are handled by the form.
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiError(response.status, message, details);
  }
  return (await response.json()) as T;
}

const json = (body: unknown) => ({ body: JSON.stringify(body) });

export interface QueueQuery {
  status: ApplicationStatus[];
  q: string;
  assignedToMe: boolean;
  sort: string;
  dir: 'asc' | 'desc';
  page: number;
  size: number;
}

export const api = {
  login: (email: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', ...json({ email, password }) }),
  me: () => request<User>('/auth/me'),

  queue: (query: QueueQuery) => {
    const params = new URLSearchParams();
    query.status.forEach((s) => params.append('status', s));
    if (query.q.trim()) params.set('q', query.q.trim());
    if (query.assignedToMe) params.set('assignedToMe', 'true');
    params.set('sort', query.sort);
    params.set('dir', query.dir);
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return request<Page<ApplicationSummary>>(`/applications?${params.toString()}`);
  },
  application: (id: string) => request<ApplicationDetail>(`/applications/${id}`),
  create: (body: CreateApplicationRequest) =>
    request<ApplicationDetail>('/applications', { method: 'POST', ...json(body) }),
  addDocument: (id: string, docType: DocumentType, fileName: string) =>
    request<ApplicationDetail>(`/applications/${id}/documents`, {
      method: 'POST',
      ...json({ docType, fileName, contentType: 'application/pdf', sizeBytes: 120000 }),
    }),
  runChecks: (id: string) => request<ApplicationDetail>(`/applications/${id}/checks/run`, { method: 'POST' }),
  manualCheck: (id: string, key: string, result: CheckResult, note: string) =>
    request<ApplicationDetail>(`/applications/${id}/checklist/${key}`, {
      method: 'PUT',
      ...json({ result, note }),
    }),
  transition: (id: string, to: ApplicationStatus, reason: string) =>
    request<ApplicationDetail>(`/applications/${id}/transitions`, {
      method: 'POST',
      ...json({ to, reason: reason.trim() || null }),
    }),

  overview: () => request<Overview>('/dashboard/overview'),
};
