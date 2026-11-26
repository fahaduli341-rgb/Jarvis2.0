import React from 'react';

export type AssistantStatus = 'OFFLINE' | 'STANDBY' | 'LISTENING' | 'THINKING' | 'SPEAKING';

interface ReactorSvgProps {
  status: AssistantStatus;
  audioLevel: number;
}

export const ReactorSvg: React.FC<ReactorSvgProps> = ({ status, audioLevel }) => {
  const isActive = status !== 'OFFLINE';

  const strokeColor =
    status === 'OFFLINE'
      ? '#334155'
      : status === 'THINKING'
        ? '#f59e0b'
        : status === 'SPEAKING'
          ? '#38bdf8'
          : status === 'STANDBY'
            ? '#0891b2'
            : '#22d3ee';

  const coreScale =
    status === 'LISTENING'
      ? 1 + Math.min(audioLevel, 1) * 0.16
      : isActive
        ? 1.02
        : 0.9;

  return (
    <div className="relative flex items-center justify-center w-56 h-56 select-none">
      <div
        className="absolute inset-4 rounded-full transition-opacity duration-200 pointer-events-none"
        style={{
          opacity: isActive ? 0.25 + audioLevel * 0.25 : 0.04,
          background: `radial-gradient(circle, ${strokeColor} 0%, rgba(11,19,43,0.4) 55%, transparent 72%)`,
        }}
      />
      <svg
        viewBox="0 0 220 220"
        className="w-full h-full"
        role="img"
        aria-label={`JARVIS reactor status ${status}`}
      >
        <g
          className={
            status === 'THINKING' || status === 'LISTENING'
              ? 'reactor-ring-outer-fast'
              : isActive
                ? 'reactor-ring-outer'
                : ''
          }
        >
          <circle
            cx="110"
            cy="110"
            r="92"
            fill="none"
            stroke={strokeColor}
            strokeWidth="3.5"
            strokeDasharray="115 78"
            strokeLinecap="round"
            opacity={isActive ? 0.9 : 0.3}
          />
        </g>

        <g className={isActive ? 'reactor-ring-inner' : ''}>
          <circle
            cx="110"
            cy="110"
            r="72"
            fill="none"
            stroke={strokeColor}
            strokeWidth="2"
            strokeDasharray="140 86"
            strokeLinecap="round"
            opacity={isActive ? 0.65 : 0.2}
          />
        </g>

        <g
          style={{
            transform: `scale(${coreScale})`,
            transformOrigin: '110px 110px',
            transition: 'transform 140ms cubic-bezier(0.16, 1, 0.3, 1)',
          }}
        >
          <circle cx="110" cy="110" r="46" fill="#0B132B" />
          <circle
            cx="110"
            cy="110"
            r="41"
            fill="none"
            stroke={strokeColor}
            strokeWidth="2.5"
            opacity={isActive ? 0.9 : 0.35}
          />
          <circle
            cx="110"
            cy="110"
            r="26"
            fill={strokeColor}
            opacity={isActive ? 0.24 : 0.08}
          />
        </g>
      </svg>
    </div>
  );
};
