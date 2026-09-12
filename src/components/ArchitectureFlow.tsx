import React, { useState } from 'react';
import { 
  ArrowRight, 
  CheckCircle2, 
  XCircle, 
  AlertTriangle, 
  ShieldCheck, 
  Database, 
  Server, 
  Network, 
  Layers, 
  RefreshCw,
  Cpu
} from 'lucide-react';

export const ArchitectureFlow: React.FC = () => {
  const [activeStep, setActiveStep] = useState<number | null>(null);

  const steps = [
    {
      id: 1,
      title: '1. Client Request',
      subtitle: 'HTTP Request',
      desc: 'Client gửi request mang theo Header: Authorization: Bearer <token>',
      icon: Network,
      color: 'blue'
    },
    {
      id: 2,
      title: '2. Netty EventLoop & Gateway',
      subtitle: 'Port 8080',
      desc: 'Spring Cloud Gateway nhận request qua Reactor Netty Non-blocking Server',
      icon: Server,
      color: 'indigo'
    },
    {
      id: 3,
      title: '3. JwtBlacklistGlobalFilter',
      subtitle: 'Order: -1 (Early phase)',
      desc: 'Filter kiểm tra excluded-paths, trích xuất token, parse claims & lấy JTI / Token ID',
      icon: ShieldCheck,
      color: 'purple'
    },
    {
      id: 4,
      title: '4. Redis Reactive Lookup',
      subtitle: 'ReactiveStringRedisTemplate',
      desc: 'Non-blocking I/O kiểm tra: hasKey("jwt:blacklist:" + tokenId) trên Redis',
      icon: Database,
      color: 'amber'
    },
    {
      id: 5,
      title: '5. Quyết định (Decision Branch)',
      subtitle: 'Pass hoặc Chặn',
      desc: 'Nếu có trong Blacklist -> Log WARN + ngắt trả 401. Nếu không -> Bổ sung headers và forward.',
      icon: Layers,
      color: 'emerald'
    }
  ];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 text-slate-100 shadow-xl">
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-800">
        <div>
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-950/60 border border-emerald-800/60 text-emerald-400 text-xs font-semibold uppercase tracking-wider mb-2">
            <Cpu className="w-3.5 h-3.5" /> Luồng Thực Thi Non-Blocking (Reactive)
          </div>
          <h2 className="text-xl font-bold text-white tracking-tight">Kiến Trúc Spring Cloud Gateway + Redis Blacklist</h2>
          <p className="text-sm text-slate-400 mt-1 max-w-2xl">
            Toàn bộ chu trình từ lúc Client gửi request, đi qua <code className="text-emerald-400 bg-slate-800 px-1.5 py-0.5 rounded text-xs">GlobalFilter (Order: -1)</code>, tra cứu Redis Reactive non-blocking, đến khi quyết định chuyển tiếp tới <code className="text-blue-400 bg-slate-800 px-1.5 py-0.5 rounded text-xs">user-auth-service</code>.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs text-slate-400 bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700/60">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
          <span>Reactor Netty EventLoop (Zero Block)</span>
        </div>
      </div>

      {/* Step boxes */}
      <div className="grid grid-cols-1 md:grid-cols-5 gap-3 my-6">
        {steps.map((step) => {
          const Icon = step.icon;
          const isSelected = activeStep === step.id;
          return (
            <button
              key={step.id}
              onClick={() => setActiveStep(isSelected ? null : step.id)}
              className={`text-left p-4 rounded-xl border transition-all duration-200 relative group cursor-pointer ${
                isSelected
                  ? 'bg-slate-800 border-emerald-500 shadow-lg shadow-emerald-950/30'
                  : 'bg-slate-950/60 border-slate-800 hover:border-slate-700 hover:bg-slate-800/50'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <div className={`p-2 rounded-lg ${
                  step.color === 'blue' ? 'bg-blue-950 text-blue-400 border border-blue-800/50' :
                  step.color === 'indigo' ? 'bg-indigo-950 text-indigo-400 border border-indigo-800/50' :
                  step.color === 'purple' ? 'bg-purple-950 text-purple-400 border border-purple-800/50' :
                  step.color === 'amber' ? 'bg-amber-950 text-amber-400 border border-amber-800/50' :
                  'bg-emerald-950 text-emerald-400 border border-emerald-800/50'
                }`}>
                  <Icon className="w-4 h-4" />
                </div>
                <span className="text-xs text-slate-500 font-mono">Step {step.id}</span>
              </div>
              <h3 className="font-semibold text-sm text-slate-200 group-hover:text-white leading-tight mb-1">
                {step.title}
              </h3>
              <p className="text-xs text-slate-400 leading-snug">{step.desc}</p>
            </button>
          );
        })}
      </div>

      {/* Visual Branch Diagram */}
      <div className="bg-slate-950 border border-slate-800/90 rounded-xl p-5">
        <h4 className="text-xs font-semibold uppercase tracking-wider text-slate-400 mb-4 flex items-center gap-2">
          <Layers className="w-4 h-4 text-emerald-400" /> Sơ Đồ Nhánh Quyết Định Tại JwtBlacklistGlobalFilter
        </h4>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {/* Branch 1: Reject (401) */}
          <div className="p-4 rounded-xl bg-red-950/20 border border-red-900/50 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2 text-red-400 font-semibold text-sm mb-2">
                <XCircle className="w-4 h-4 text-red-400 shrink-0" />
                <span>Nhánh 1: Từ Chối Request (HTTP 401)</span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed mb-3">
                Xảy ra khi:
              </p>
              <ul className="text-xs text-slate-300 space-y-1.5 list-disc list-inside">
                <li>Header Authorization bị thiếu hoặc không bắt đầu bằng <code className="text-red-300 bg-red-950 px-1 py-0.5 rounded">Bearer</code>.</li>
                <li>Token không hợp lệ, hết hạn, hoặc bị sửa đổi trái phép (chữ ký sai).</li>
                <li>
                  <strong className="text-amber-300">ĐẶC BIỆT:</strong> Token có trong Redis Blacklist (<code className="text-amber-300 bg-slate-900 px-1 py-0.5 rounded">hasKey == true</code>).
                </li>
              </ul>
            </div>

            <div className="mt-4 pt-3 border-t border-red-900/40 bg-slate-900/80 p-3 rounded-lg font-mono text-[11px] text-red-300">
              <div className="text-amber-400 flex items-center gap-1.5 mb-1">
                <AlertTriangle className="w-3.5 h-3.5" /> Ghi Log WARN:
              </div>
              <div className="text-slate-400">
                [WARN] JwtBlacklistGlobalFilter - WARNING: Revoked/Blacklisted JWT detected! Token ID: [jti-123], Path: [/api/v1/users/me]
              </div>
              <div className="text-red-400 mt-1">
                Response: 401 Unauthorized (Dừng chuỗi filter, không gọi backend)
              </div>
            </div>
          </div>

          {/* Branch 2: Pass (200 / Forward) */}
          <div className="p-4 rounded-xl bg-emerald-950/20 border border-emerald-900/50 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2 text-emerald-400 font-semibold text-sm mb-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                <span>Nhánh 2: Cho Phép Đi Tiếp (Forward)</span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed mb-3">
                Xảy ra khi:
              </p>
              <ul className="text-xs text-slate-300 space-y-1.5 list-disc list-inside">
                <li>Đường dẫn thuộc danh sách Public Whitelist (<code className="text-emerald-300 bg-emerald-950 px-1 py-0.5 rounded">excludedPaths</code>).</li>
                <li>Token có định dạng hợp lệ, chữ ký đúng, còn hạn dùng.</li>
                <li>
                  <strong className="text-emerald-300">ĐẶC BIỆT:</strong> Token KHÔNG tồn tại trong Redis Blacklist (<code className="text-emerald-300 bg-slate-900 px-1 py-0.5 rounded">hasKey == false</code>).
                </li>
              </ul>
            </div>

            <div className="mt-4 pt-3 border-t border-emerald-900/40 bg-slate-900/80 p-3 rounded-lg font-mono text-[11px] text-emerald-300">
              <div className="text-blue-400 flex items-center gap-1.5 mb-1">
                <ArrowRight className="w-3.5 h-3.5" /> Bổ sung Header định danh & Forward:
              </div>
              <div className="text-slate-300">
                mutatedRequest = request.mutate()<br />
                &nbsp;&nbsp;.header("X-Auth-User-Id", userId)<br />
                &nbsp;&nbsp;.header("X-Auth-Token-Id", jti).build();
              </div>
              <div className="text-emerald-400 mt-1">
                chain.filter(exchange) -&gt; Proxy tới user-auth-service (8081)
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
