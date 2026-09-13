import React from 'react';
import { ShieldCheck, Lock, CheckCircle2, ArrowRight, XCircle, Layers, Cpu, Server } from 'lucide-react';

export default function App() {
  return (
    <div id="service-container" className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6 font-sans">
      <div id="service-card" className="max-w-3xl w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl space-y-6">
        
        {/* Header */}
        <div id="service-header" className="flex items-center gap-4 border-b border-slate-800/80 pb-5">
          <div id="service-icon" className="w-12 h-12 rounded-xl bg-emerald-600/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-7 h-7" />
          </div>
          <div>
            <h1 id="service-title" className="text-xl font-bold text-white tracking-tight">
              Microservices Architecture: Authen vs Author
            </h1>
            <p id="service-subtitle" className="text-sm text-slate-400">
              API Gateway (Authentication) ──► Resource Service (Authorization Only)
            </p>
          </div>
        </div>

        {/* Comparison Grid */}
        <div id="service-comparison" className="grid grid-cols-1 md:grid-cols-2 gap-4">
          
          {/* Service 1: API Gateway */}
          <div id="gateway-card" className="p-5 rounded-xl bg-slate-950/70 border border-emerald-900/40 space-y-3">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-2 text-sm font-semibold text-emerald-400">
                <Server className="w-4 h-4" />
                API Gateway (:8080)
              </span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 border border-emerald-800/60">
                Perimeter Ingress
              </span>
            </div>
            <div className="text-xs text-slate-300 font-medium">
              Chịu trách nhiệm: <strong className="text-emerald-400">Authentication (Authen)</strong>
            </div>
            <ul className="space-y-1.5 text-xs text-slate-400">
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Kiến trúc phân tầng: <code className="text-slate-300">core</code>, <code className="text-slate-300">security</code>, <code className="text-slate-300">filter/global</code></span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Distributed Tracing (<code className="text-slate-300">X-Correlation-Id</code>)</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Xác thực chữ ký Keycloak RS256 JWKS</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Check Redis Blacklist (<code className="text-slate-300">jwt:blacklist:jti</code>)</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Resilience4j Circuit Breaker & Fallback Handler</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400 shrink-0" />
                <span>Bổ sung headers tin cậy (<code className="text-slate-300">X-Auth-*</code>)</span>
              </li>
            </ul>
          </div>

          {/* Service 2: Resource Service */}
          <div id="resource-card" className="p-5 rounded-xl bg-slate-950/70 border border-sky-900/40 space-y-3">
            <div className="flex items-center justify-between">
              <span className="flex items-center gap-2 text-sm font-semibold text-sky-400">
                <Layers className="w-4 h-4" />
                Resource Service (:8082)
              </span>
              <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-sky-950 text-sky-300 border border-sky-800/60">
                Downstream Domain
              </span>
            </div>
            <div className="text-xs text-slate-300 font-medium">
              Chịu trách nhiệm: <strong className="text-sky-400">Authorization (Author / RBAC)</strong>
            </div>
            <ul className="space-y-1.5 text-xs text-slate-400">
              <li className="flex items-center gap-2">
                <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                <span className="text-rose-300/90 font-medium">KHÔNG cần check Authen hay JWKS RSA</span>
              </li>
              <li className="flex items-center gap-2">
                <XCircle className="w-3.5 h-3.5 text-rose-400 shrink-0" />
                <span className="text-rose-300/90 font-medium">KHÔNG cần kết nối Redis Blacklist</span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-sky-400 shrink-0" />
                <span>Đọc vai trò người dùng từ <code className="text-slate-300">X-Auth-Roles</code></span>
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-sky-400 shrink-0" />
                <span>Kiểm tra quyền nghiệp vụ (<code className="text-slate-300">@PreAuthorize</code>, 403)</span>
              </li>
            </ul>
          </div>

        </div>

        {/* Why no Authen summary box */}
        <div id="why-no-authen-box" className="p-4 rounded-xl bg-slate-950/50 border border-slate-800 text-xs text-slate-300 space-y-2">
          <div className="flex items-center gap-2 font-semibold text-white">
            <Cpu className="w-4 h-4 text-amber-400" />
            <span>Tại sao Resource Service KHÔNG CẦN check Authen nữa?</span>
          </div>
          <p className="text-slate-400 leading-relaxed">
            1. <strong>Perimeter Defense:</strong> Mọi request đi từ ngoài vào đều bắt buộc phải qua API Gateway. Gateway đã cam kết 100% người dùng là thật và token chưa logout.<br/>
            2. <strong>Zero Redundant Latency:</strong> Tiết kiệm tải CPU (tính toán mật mã giải mã RSA) và loại bỏ hoàn toàn các lượt gọi thừa đến Redis cho mỗi hop dịch vụ nội bộ.<br/>
            3. <strong>Tách biệt mối bận tâm (Separation of Concerns):</strong> Gateway lo bảo mật lớp mạng (Security Gateway), Resource Service chỉ lo nghiệp vụ phân quyền người dùng (Business Authorization).
          </p>
        </div>

        {/* Footer info */}
        <div className="flex items-center justify-between text-[11px] text-slate-500 pt-2 border-t border-slate-800/80">
          <span>Xem chi tiết tài liệu tại <code className="text-slate-400 font-mono">README.md</code></span>
          <span>Source: <code className="text-slate-400 font-mono">/api-gateway-service</code> & <code className="text-slate-400 font-mono">/resource-service</code></span>
        </div>

      </div>
    </div>
  );
}
