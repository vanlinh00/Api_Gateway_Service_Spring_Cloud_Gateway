import React from 'react';
import { ShieldCheck, Server, KeyRound, Database, ArrowRight, Terminal } from 'lucide-react';

export default function App() {
  return (
    <div id="service-container" className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-6 font-sans">
      <div id="service-card" className="max-w-2xl w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-2xl">
        <div id="service-header" className="flex items-center gap-4 border-b border-slate-800/80 pb-6 mb-6">
          <div id="service-icon" className="w-12 h-12 rounded-xl bg-emerald-600/20 text-emerald-400 border border-emerald-500/30 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-7 h-7" />
          </div>
          <div>
            <h1 id="service-title" className="text-xl font-bold text-white tracking-tight">
              API Gateway Service
            </h1>
            <p id="service-subtitle" className="text-sm text-slate-400">
              Spring Cloud Gateway • Keycloak JWT • Redis Reactive Blacklist
            </p>
          </div>
        </div>

        <div id="service-status" className="space-y-4 mb-8 text-sm">
          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <span className="flex items-center gap-2 text-slate-300">
              <Server className="w-4 h-4 text-emerald-400" />
              Runtime Architecture
            </span>
            <span className="font-mono text-xs text-emerald-400 bg-emerald-950/60 px-2.5 py-1 rounded-md border border-emerald-800/50">
              Backend Microservice Only (/api-gateway-service)
            </span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <span className="flex items-center gap-2 text-slate-300">
              <KeyRound className="w-4 h-4 text-amber-400" />
              Identity Provider (IdP)
            </span>
            <span className="font-mono text-xs text-slate-300">Keycloak OIDC (JWKS RS256)</span>
          </div>

          <div className="flex items-center justify-between p-3 rounded-xl bg-slate-950/60 border border-slate-800">
            <span className="flex items-center gap-2 text-slate-300">
              <Database className="w-4 h-4 text-rose-400" />
              Revocation Store
            </span>
            <span className="font-mono text-xs text-slate-300">Redis Reactive (Lettuce Non-blocking)</span>
          </div>
        </div>

        <div id="service-commands" className="bg-slate-950 rounded-xl p-4 border border-slate-800/80 mb-6 font-mono text-xs">
          <div className="flex items-center gap-2 text-slate-400 mb-2 font-sans font-semibold">
            <Terminal className="w-3.5 h-3.5 text-slate-400" />
            <span>Launch Service with Keycloak & Redis</span>
          </div>
          <div className="text-emerald-400 select-all">
            cd api-gateway-service && docker compose up -d
          </div>
        </div>

        <p id="service-note" className="text-xs text-slate-500 text-center">
          All frontend dashboard components have been removed. Read <code className="text-slate-400 font-mono">/api-gateway-service/README.md</code> for functional specifications.
        </p>
      </div>
    </div>
  );
}
