export interface ProjectFile {
  path: string;
  name: string;
  language: string;
  category: 'config' | 'filter' | 'java' | 'docker' | 'doc' | 'test';
  content: string;
  description: string;
}

export interface RedisBlacklistEntry {
  key: string;
  tokenId: string;
  userId: string;
  revokedAt: string;
  reason: string;
  ttlSeconds: number;
}

export interface GatewayLogEntry {
  id: string;
  timestamp: string;
  level: 'INFO' | 'WARN' | 'ERROR' | 'DEBUG';
  logger: string;
  thread: string;
  message: string;
}

export interface SimulationResult {
  statusCode: number;
  statusText: string;
  latencyMs: number;
  responseHeaders: Record<string, string>;
  responseBody: Record<string, unknown> | string;
  isBlacklisted: boolean;
  filterBypassed: boolean;
  targetServiceUrl?: string;
  logs: GatewayLogEntry[];
}
