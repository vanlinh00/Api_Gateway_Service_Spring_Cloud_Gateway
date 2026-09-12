import React, { useState } from 'react';
import { 
  Play, 
  RotateCcw, 
  Trash2, 
  Plus, 
  CheckCircle2, 
  XCircle, 
  AlertTriangle, 
  Database, 
  Terminal, 
  ShieldAlert, 
  ShieldCheck, 
  Sparkles,
  Server,
  ArrowRight
} from 'lucide-react';
import { RedisBlacklistEntry, GatewayLogEntry, SimulationResult } from '../types';

const INITIAL_BLACKLIST: RedisBlacklistEntry[] = [
  {
    key: 'jwt:blacklist:jti-revoked-001',
    tokenId: 'jti-revoked-001',
    userId: 'user-alice-102',
    revokedAt: '2026-09-12 12:30:15',
    reason: 'Người dùng chủ động Logout',
    ttlSeconds: 3450
  },
  {
    key: 'jwt:blacklist:jti-compromised-999',
    tokenId: 'jti-compromised-999',
    userId: 'user-bob-505',
    revokedAt: '2026-09-12 12:45:00',
    reason: 'Phát hiện nghi vấn rò rỉ token',
    ttlSeconds: 1800
  }
];

export const Simulator: React.FC = () => {
  // Simulator Request State
  const [method, setMethod] = useState<'GET' | 'POST' | 'PUT' | 'DELETE'>('GET');
  const [path, setPath] = useState('/api/v1/users/profile');
  const [authHeader, setAuthHeader] = useState('Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZUBleGFtcGxlLmNvbSIsImp0aSI6Imp0aS1yZXZva2VkLTAwMSIsImV4cCI6MTgwMDAwMDAwMH0.mockSignature');
  const [tokenId, setTokenId] = useState('jti-revoked-001');
  const [userId, setUserId] = useState('alice@example.com');

  // Redis Blacklist state
  const [blacklist, setBlacklist] = useState<RedisBlacklistEntry[]>(INITIAL_BLACKLIST);
  const [newKeyJti, setNewKeyJti] = useState('');
  const [newKeyUser, setNewKeyUser] = useState('');
  const [newKeyReason, setNewKeyReason] = useState('Logout');

  // Simulation execution state
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<SimulationResult | null>(null);
  const [allLogs, setAllLogs] = useState<GatewayLogEntry[]>([
    {
      id: 'init-1',
      timestamp: '2026-09-12 13:00:00.102',
      level: 'INFO',
      thread: 'main',
      logger: 'c.e.gateway.ApiGatewayApplication',
      message: 'Started ApiGatewayApplication in 1.452 seconds (process running for 2.105)'
    },
    {
      id: 'init-2',
      timestamp: '2026-09-12 13:00:00.105',
      level: 'INFO',
      thread: 'main',
      logger: 'c.e.g.config.RedisConfig',
      message: 'ReactiveStringRedisTemplate initialized with Lettuce reactive non-blocking connection factory.'
    }
  ]);

  // Presets
  const applyPreset = (type: 'blacklisted' | 'valid' | 'missing' | 'malformed' | 'public') => {
    if (type === 'blacklisted') {
      setMethod('GET');
      setPath('/api/v1/users/profile');
      setTokenId('jti-revoked-001');
      setUserId('alice@example.com');
      setAuthHeader('Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhbGljZUBleGFtcGxlLmNvbSIsImp0aSI6Imp0aS1yZXZva2VkLTAwMSJ9.signature');
    } else if (type === 'valid') {
      setMethod('GET');
      setPath('/api/v1/users/profile');
      setTokenId('jti-active-888');
      setUserId('john_doe@example.com');
      setAuthHeader('Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJqb2huX2RvZUBleGFtcGxlLmNvbSIsImp0aSI6Imp0aS1hY3RpdmUtODg4In0.signature');
    } else if (type === 'missing') {
      setMethod('GET');
      setPath('/api/v1/users/profile');
      setAuthHeader('');
      setTokenId('');
    } else if (type === 'malformed') {
      setMethod('GET');
      setPath('/api/v1/users/profile');
      setAuthHeader('InvalidHeaderFormat 123456');
      setTokenId('unknown');
    } else if (type === 'public') {
      setMethod('POST');
      setPath('/api/v1/auth/login');
      setAuthHeader('');
      setTokenId('');
    }
  };

  const handleAddBlacklist = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newKeyJti.trim()) return;
    const jti = newKeyJti.trim();
    const entry: RedisBlacklistEntry = {
      key: `jwt:blacklist:${jti}`,
      tokenId: jti,
      userId: newKeyUser.trim() || 'user@example.com',
      revokedAt: new Date().toISOString().replace('T', ' ').substring(0, 19),
      reason: newKeyReason,
      ttlSeconds: 3600
    };
    setBlacklist(prev => [entry, ...prev]);
    setNewKeyJti('');
    setNewKeyUser('');
  };

  const handleRemoveBlacklist = (keyToRemove: string) => {
    setBlacklist(prev => prev.filter(item => item.key !== keyToRemove));
  };

  // Run the Spring Cloud Gateway logic
  const handleExecuteRequest = () => {
    setIsRunning(true);

    setTimeout(() => {
      const now = new Date();
      const timeStr = now.toISOString().replace('T', ' ').substring(0, 23);
      const isPublic = path === '/api/v1/auth/login' || 
                       path === '/api/v1/auth/register' || 
                       path.startsWith('/actuator');

      const logs: GatewayLogEntry[] = [];

      // Step 1: Log incoming request
      logs.push({
        id: `log-${Date.now()}-1`,
        timestamp: timeStr,
        level: 'DEBUG',
        thread: 'reactor-http-epoll-2',
        logger: 'c.e.g.f.JwtBlacklistGlobalFilter',
        message: `Processing request: ${method} ${path}`
      });

      // Condition 1: Public endpoint
      if (isPublic) {
        logs.push({
          id: `log-${Date.now()}-2`,
          timestamp: timeStr,
          level: 'DEBUG',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.JwtBlacklistGlobalFilter',
          message: `Path [${path}] is public/excluded. Skipping JWT Blacklist check.`
        });
        logs.push({
          id: `log-${Date.now()}-3`,
          timestamp: timeStr,
          level: 'INFO',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.RequestLoggingFilter',
          message: `[GATEWAY-ACCESS] ${method} ${path} -> Status: 200 (took 14 ms)`
        });

        const simResult: SimulationResult = {
          statusCode: 200,
          statusText: 'OK',
          latencyMs: 14,
          responseHeaders: {
            'content-type': 'application/json',
            'x-gateway-name': 'api-gateway-service',
            'x-gateway-processed-time': String(Date.now())
          },
          responseBody: {
            service: 'user-auth-service (Mock)',
            status: 'success',
            message: 'Public endpoint executed successfully'
          },
          isBlacklisted: false,
          filterBypassed: true,
          targetServiceUrl: 'http://user-auth-service:8081' + path,
          logs
        };
        setResult(simResult);
        setAllLogs(prev => [...logs, ...prev]);
        setIsRunning(false);
        return;
      }

      // Condition 2: Missing or invalid header format
      if (!authHeader || !authHeader.startsWith('Bearer ')) {
        logs.push({
          id: `log-${Date.now()}-4`,
          timestamp: timeStr,
          level: 'WARN',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.JwtBlacklistGlobalFilter',
          message: `Missing or malformed Authorization header for request to path: [${path}]`
        });
        logs.push({
          id: `log-${Date.now()}-5`,
          timestamp: timeStr,
          level: 'INFO',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.RequestLoggingFilter',
          message: `[GATEWAY-ACCESS] ${method} ${path} -> Status: 401 (took 3 ms)`
        });

        const simResult: SimulationResult = {
          statusCode: 401,
          statusText: 'Unauthorized',
          latencyMs: 3,
          responseHeaders: {
            'content-type': 'application/json'
          },
          responseBody: {
            timestamp: timeStr,
            status: 401,
            error: 'Unauthorized',
            message: 'Missing or invalid Authorization header',
            path: path
          },
          isBlacklisted: false,
          filterBypassed: false,
          logs
        };
        setResult(simResult);
        setAllLogs(prev => [...logs, ...prev]);
        setIsRunning(false);
        return;
      }

      // Condition 3: Check Redis Blacklist
      const redisKey = `jwt:blacklist:${tokenId}`;
      const foundInBlacklist = blacklist.find(b => b.key === redisKey || b.tokenId === tokenId);

      if (foundInBlacklist) {
        // TOKEN IS IN BLACKLIST -> Log WARN and reject 401
        logs.push({
          id: `log-${Date.now()}-6`,
          timestamp: timeStr,
          level: 'WARN',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.JwtBlacklistGlobalFilter',
          message: `WARNING: Revoked/Blacklisted JWT detected! Token ID: [${tokenId}], Subject: [${userId}], Path: [${path}], Client IP: [127.0.0.1]`
        });
        logs.push({
          id: `log-${Date.now()}-7`,
          timestamp: timeStr,
          level: 'INFO',
          thread: 'reactor-http-epoll-2',
          logger: 'c.e.g.f.RequestLoggingFilter',
          message: `[GATEWAY-ACCESS] ${method} ${path} -> Status: 401 (took 5 ms)`
        });

        const simResult: SimulationResult = {
          statusCode: 401,
          statusText: 'Unauthorized',
          latencyMs: 5,
          responseHeaders: {
            'content-type': 'application/json'
          },
          responseBody: {
            timestamp: timeStr,
            status: 401,
            error: 'Unauthorized',
            message: 'Token has been revoked/blacklisted. Please log in again.',
            path: path
          },
          isBlacklisted: true,
          filterBypassed: false,
          logs
        };
        setResult(simResult);
        setAllLogs(prev => [...logs, ...prev]);
        setIsRunning(false);
        return;
      }

      // Condition 4: TOKEN IS VALID & NOT BLACKLISTED -> Forward request
      logs.push({
        id: `log-${Date.now()}-8`,
        timestamp: timeStr,
        level: 'DEBUG',
        thread: 'reactor-http-epoll-2',
        logger: 'c.e.g.f.JwtBlacklistGlobalFilter',
        message: `Token [${tokenId}] is valid and not blacklisted. Forwarding request to downstream service...`
      });
      logs.push({
        id: `log-${Date.now()}-9`,
        timestamp: timeStr,
        level: 'INFO',
        thread: 'reactor-http-epoll-2',
        logger: 'c.e.g.f.RequestLoggingFilter',
        message: `[GATEWAY-ACCESS] ${method} ${path} -> Status: 200 (took 18 ms)`
      });

      const simResult: SimulationResult = {
        statusCode: 200,
        statusText: 'OK',
        latencyMs: 18,
        responseHeaders: {
          'content-type': 'application/json',
          'x-gateway-name': 'api-gateway-service',
          'x-auth-user-id': userId,
          'x-auth-token-id': tokenId,
          'x-gateway-processed-time': String(Date.now())
        },
        responseBody: {
          service: 'user-auth-service',
          endpoint: path,
          authenticatedUser: userId,
          profile: {
            id: 'u-1029',
            email: userId,
            roles: ['ROLE_USER', 'ROLE_DEVELOPER'],
            status: 'ACTIVE'
          }
        },
        isBlacklisted: false,
        filterBypassed: false,
        targetServiceUrl: 'http://user-auth-service:8081' + path,
        logs
      };
      setResult(simResult);
      setAllLogs(prev => [...logs, ...prev]);
      setIsRunning(false);
    }, 400);
  };

  return (
    <div className="space-y-6">
      {/* Scenario Presets Banner */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
        <div className="flex items-center gap-2 mb-3">
          <Sparkles className="w-4 h-4 text-emerald-400" />
          <h3 className="text-sm font-bold text-slate-100 uppercase tracking-wider">
            Chọn Kịch Bản Kiểm Thử Nhanh (5 Trường Hợp Yêu Cầu)
          </h3>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2.5">
          <button
            onClick={() => applyPreset('blacklisted')}
            className="p-3 rounded-xl bg-slate-950 border border-red-900/60 hover:border-red-500 hover:bg-red-950/20 text-left transition cursor-pointer group"
          >
            <div className="flex items-center gap-1.5 text-red-400 text-xs font-semibold mb-1">
              <ShieldAlert className="w-3.5 h-3.5 shrink-0" />
              <span>1. Token Bị Blacklist</span>
            </div>
            <p className="text-[11px] text-slate-400">Token có trong Redis -&gt; Chặn 401 + Log WARN</p>
          </button>

          <button
            onClick={() => applyPreset('valid')}
            className="p-3 rounded-xl bg-slate-950 border border-emerald-900/60 hover:border-emerald-500 hover:bg-emerald-950/20 text-left transition cursor-pointer group"
          >
            <div className="flex items-center gap-1.5 text-emerald-400 text-xs font-semibold mb-1">
              <ShieldCheck className="w-3.5 h-3.5 shrink-0" />
              <span>2. Token Hợp Lệ</span>
            </div>
            <p className="text-[11px] text-slate-400">Không có trong Redis -&gt; Pass filter (200 OK)</p>
          </button>

          <button
            onClick={() => applyPreset('missing')}
            className="p-3 rounded-xl bg-slate-950 border border-amber-900/60 hover:border-amber-500 hover:bg-amber-950/20 text-left transition cursor-pointer group"
          >
            <div className="flex items-center gap-1.5 text-amber-400 text-xs font-semibold mb-1">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
              <span>3. Không Có Token</span>
            </div>
            <p className="text-[11px] text-slate-400">Thiếu Authorization -&gt; Chặn ngay 401</p>
          </button>

          <button
            onClick={() => applyPreset('malformed')}
            className="p-3 rounded-xl bg-slate-950 border border-slate-800 hover:border-slate-600 hover:bg-slate-800/40 text-left transition cursor-pointer group"
          >
            <div className="flex items-center gap-1.5 text-purple-400 text-xs font-semibold mb-1">
              <XCircle className="w-3.5 h-3.5 shrink-0" />
              <span>4. Sai Định Dạng</span>
            </div>
            <p className="text-[11px] text-slate-400">Không bắt đầu Bearer -&gt; Chặn 401</p>
          </button>

          <button
            onClick={() => applyPreset('public')}
            className="p-3 rounded-xl bg-slate-950 border border-blue-900/60 hover:border-blue-500 hover:bg-blue-950/20 text-left transition cursor-pointer group"
          >
            <div className="flex items-center gap-1.5 text-blue-400 text-xs font-semibold mb-1">
              <Server className="w-3.5 h-3.5 shrink-0" />
              <span>5. Route Public</span>
            </div>
            <p className="text-[11px] text-slate-400">/api/v1/auth/login -&gt; Bỏ qua filter</p>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left Column: Request Builder (7 cols) */}
        <div className="lg:col-span-7 space-y-6">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
            <h3 className="text-sm font-bold text-slate-100 mb-4 flex items-center justify-between">
              <span className="flex items-center gap-2">
                <Server className="w-4 h-4 text-emerald-400" />
                Gửi HTTP Request Đến API Gateway
              </span>
              <span className="text-xs font-mono text-slate-400">Target: http://localhost:8080</span>
            </h3>

            {/* Method + Path */}
            <div className="flex gap-2 mb-4">
              <select
                value={method}
                onChange={(e) => setMethod(e.target.value as any)}
                className="bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-xl px-3 py-2 font-mono font-bold focus:outline-none focus:border-emerald-500"
              >
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="DELETE">DELETE</option>
              </select>
              <input
                type="text"
                value={path}
                onChange={(e) => setPath(e.target.value)}
                placeholder="/api/v1/users/profile"
                className="flex-1 bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-xl px-3.5 py-2 font-mono focus:outline-none focus:border-emerald-500"
              />
            </div>

            {/* Token ID (JTI) & User ID info */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
              <div>
                <label className="block text-[11px] font-medium text-slate-400 mb-1">
                  JWT Identifier (Claim "jti")
                </label>
                <input
                  type="text"
                  value={tokenId}
                  onChange={(e) => setTokenId(e.target.value)}
                  placeholder="jti-revoked-001"
                  className="w-full bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-xl px-3 py-2 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>
              <div>
                <label className="block text-[11px] font-medium text-slate-400 mb-1">
                  User Subject (Claim "sub")
                </label>
                <input
                  type="text"
                  value={userId}
                  onChange={(e) => setUserId(e.target.value)}
                  placeholder="alice@example.com"
                  className="w-full bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-xl px-3 py-2 font-mono focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>

            {/* Header Authorization */}
            <div className="mb-5">
              <label className="block text-[11px] font-medium text-slate-400 mb-1">
                Header: <code className="text-emerald-400">Authorization</code>
              </label>
              <textarea
                rows={2}
                value={authHeader}
                onChange={(e) => setAuthHeader(e.target.value)}
                placeholder="Bearer <JWT_TOKEN_HERE>"
                className="w-full bg-slate-950 border border-slate-700 text-slate-300 text-xs rounded-xl p-3 font-mono focus:outline-none focus:border-emerald-500 resize-none leading-relaxed"
              />
            </div>

            {/* Submit Action */}
            <div className="flex items-center justify-between">
              <button
                onClick={handleExecuteRequest}
                disabled={isRunning}
                className="flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 disabled:opacity-50 text-white text-xs font-bold shadow-lg shadow-emerald-950/60 transition cursor-pointer"
              >
                <Play className="w-4 h-4 fill-current" />
                {isRunning ? 'Đang lọc qua Gateway...' : 'Gửi Request Qua Gateway'}
              </button>

              <button
                onClick={() => applyPreset('blacklisted')}
                className="text-xs text-slate-400 hover:text-slate-200 flex items-center gap-1.5 cursor-pointer"
              >
                <RotateCcw className="w-3.5 h-3.5" /> Reset
              </button>
            </div>
          </div>

          {/* Response Inspector */}
          {result && (
            <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
              <div className="flex items-center justify-between pb-3 mb-3 border-b border-slate-800">
                <div className="flex items-center gap-2">
                  <span className={`px-2.5 py-1 rounded-lg text-xs font-bold font-mono ${
                    result.statusCode === 200 
                      ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' 
                      : 'bg-red-950 text-red-400 border border-red-800'
                  }`}>
                    HTTP {result.statusCode} {result.statusText}
                  </span>
                  <span className="text-xs text-slate-400">
                    Thời gian: <strong className="text-slate-200 font-mono">{result.latencyMs} ms</strong>
                  </span>
                </div>

                {result.isBlacklisted ? (
                  <span className="inline-flex items-center gap-1 text-xs text-red-400 bg-red-950/50 px-2 py-0.5 rounded border border-red-800/40">
                    <ShieldAlert className="w-3 h-3" /> Token Bị Blacklist (401)
                  </span>
                ) : result.filterBypassed ? (
                  <span className="inline-flex items-center gap-1 text-xs text-blue-400 bg-blue-950/50 px-2 py-0.5 rounded border border-blue-800/40">
                    <CheckCircle2 className="w-3 h-3" /> Public Endpoint (Bypass)
                  </span>
                ) : (
                  <span className="inline-flex items-center gap-1 text-xs text-emerald-400 bg-emerald-950/50 px-2 py-0.5 rounded border border-emerald-800/40">
                    <CheckCircle2 className="w-3 h-3" /> Forwarded to Downstream (200)
                  </span>
                )}
              </div>

              {result.targetServiceUrl && (
                <div className="text-xs font-mono text-emerald-400/90 bg-slate-950 p-2.5 rounded-lg border border-slate-800 mb-3 flex items-center gap-2">
                  <ArrowRight className="w-3.5 h-3.5 shrink-0" />
                  <span>Forwarded target: {result.targetServiceUrl}</span>
                </div>
              )}

              {/* Headers added */}
              <div className="mb-3">
                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                  Response Headers
                </span>
                <div className="mt-1 bg-slate-950 p-2.5 rounded-lg border border-slate-800 font-mono text-[11px] text-slate-300 space-y-1">
                  {Object.entries(result.responseHeaders).map(([k, v]) => (
                    <div key={k} className="flex justify-between">
                      <span className="text-slate-500">{k}:</span>
                      <span className="text-slate-300 truncate max-w-[280px]">{v}</span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Payload */}
              <div>
                <span className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                  Response Body (JSON)
                </span>
                <pre className="mt-1 bg-slate-950 p-3 rounded-lg border border-slate-800 font-mono text-xs text-slate-200 overflow-x-auto">
                  {JSON.stringify(result.responseBody, null, 2)}
                </pre>
              </div>
            </div>
          )}
        </div>

        {/* Right Column: Redis Blacklist Manager & Gateway Console (5 cols) */}
        <div className="lg:col-span-5 space-y-6">
          {/* Redis Blacklist Table */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4 text-amber-400" />
                <h3 className="text-sm font-bold text-slate-100">Redis Blacklist Keys</h3>
              </div>
              <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-800 text-amber-400 border border-slate-700">
                {blacklist.length} Keys
              </span>
            </div>

            <p className="text-xs text-slate-400 mb-3">
              Khi User logout hoặc đổi mật khẩu, Service đưa Token ID vào Redis với prefix <code className="text-amber-400">jwt:blacklist:</code>.
            </p>

            {/* List */}
            <div className="space-y-2 max-h-[220px] overflow-y-auto pr-1 mb-4">
              {blacklist.length === 0 ? (
                <div className="text-center py-6 text-xs text-slate-500 bg-slate-950 rounded-xl border border-slate-800/80">
                  Không có token nào trong Blacklist
                </div>
              ) : (
                blacklist.map((item) => (
                  <div
                    key={item.key}
                    className="p-2.5 rounded-xl bg-slate-950 border border-slate-800 text-xs flex items-center justify-between group"
                  >
                    <div className="min-w-0 pr-2">
                      <div className="font-mono text-[11px] font-bold text-amber-400 truncate">
                        {item.key}
                      </div>
                      <div className="text-[10px] text-slate-400 truncate mt-0.5">
                        {item.userId} • {item.reason} • TTL: {item.ttlSeconds}s
                      </div>
                    </div>
                    <button
                      onClick={() => handleRemoveBlacklist(item.key)}
                      title="Gỡ khỏi Blacklist (Cho phép token dùng lại)"
                      className="p-1.5 rounded-lg text-slate-500 hover:text-red-400 hover:bg-slate-800 transition cursor-pointer"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))
              )}
            </div>

            {/* Add new key form */}
            <form onSubmit={handleAddBlacklist} className="pt-3 border-t border-slate-800/80">
              <div className="text-xs font-semibold text-slate-300 mb-2 flex items-center gap-1">
                <Plus className="w-3.5 h-3.5 text-emerald-400" /> Thêm Token ID Thu Hồi Vào Redis:
              </div>
              <div className="grid grid-cols-2 gap-2 mb-2">
                <input
                  type="text"
                  placeholder="jti-token-xyz"
                  value={newKeyJti}
                  onChange={(e) => setNewKeyJti(e.target.value)}
                  className="bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-lg px-2.5 py-1.5 font-mono focus:outline-none focus:border-amber-500"
                />
                <input
                  type="text"
                  placeholder="user@example.com"
                  value={newKeyUser}
                  onChange={(e) => setNewKeyUser(e.target.value)}
                  className="bg-slate-950 border border-slate-700 text-slate-200 text-xs rounded-lg px-2.5 py-1.5 focus:outline-none focus:border-amber-500"
                />
              </div>
              <button
                type="submit"
                className="w-full py-1.5 bg-amber-600 hover:bg-amber-500 text-slate-950 font-bold text-xs rounded-lg transition cursor-pointer"
              >
                Thu Hồi Token (SET jwt:blacklist:&lt;jti&gt;)
              </button>
            </form>
          </div>

          {/* Gateway Console Stream */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 shadow-xl">
            <div className="flex items-center justify-between mb-3">
              <div className="flex items-center gap-2">
                <Terminal className="w-4 h-4 text-cyan-400" />
                <h3 className="text-sm font-bold text-slate-100">Gateway Log Stream (SLF4J)</h3>
              </div>
              <button
                onClick={() => setAllLogs([])}
                className="text-[11px] text-slate-400 hover:text-slate-200 cursor-pointer"
              >
                Xóa logs
              </button>
            </div>

            <div className="bg-slate-950 border border-slate-800 rounded-xl p-3 font-mono text-[11px] leading-relaxed max-h-[260px] overflow-y-auto space-y-2">
              {allLogs.map((l) => (
                <div key={l.id} className="border-b border-slate-900/60 pb-1.5 last:border-0">
                  <div className="flex items-center gap-2">
                    <span className="text-slate-600">{l.timestamp}</span>
                    <span className={`px-1 rounded text-[10px] font-bold ${
                      l.level === 'WARN' ? 'bg-amber-950 text-amber-400 border border-amber-800/60' :
                      l.level === 'INFO' ? 'bg-blue-950 text-blue-400' :
                      l.level === 'DEBUG' ? 'bg-slate-800 text-slate-400' :
                      'bg-red-950 text-red-400'
                    }`}>
                      {l.level}
                    </span>
                    <span className="text-slate-500 truncate max-w-[120px]">{l.logger}</span>
                  </div>
                  <div className={`mt-0.5 break-all ${
                    l.level === 'WARN' ? 'text-amber-300 font-semibold' : 'text-slate-300'
                  }`}>
                    {l.message}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
