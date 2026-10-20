const imgHero = "https://www.figma.com/api/mcp/asset/mock-hero";
const imgAvatar = "https://www.figma.com/api/mcp/asset/mock-avatar";

type FeatureProps = { title: string; body: string };

function Feature({ title, body }: FeatureProps) {
  return (
    <div className="flex items-start gap-3" data-node-id="12:410">
      <div className="mt-1 size-5 shrink-0 rounded-full bg-[#e8ecff] flex items-center justify-center">
        <div className="size-2 rounded-full bg-[#4f46e5]" />
      </div>
      <div className="flex flex-col gap-1">
        <p className="text-[15px] font-semibold leading-5 text-[#0f172a]">{title}</p>
        <p className="text-[14px] leading-5 text-[#475569]">{body}</p>
      </div>
    </div>
  );
}

export default function ProductLaunchCard() {
  return (
    <div className="min-h-screen w-full bg-[#f1f5f9] flex items-center justify-center p-8" data-name="Product launch" data-node-id="12:345">
      <div className="w-full max-w-[880px] overflow-hidden rounded-[24px] bg-white shadow-[0px_24px_48px_-12px_rgba(15,23,42,0.18)] grid md:grid-cols-2">
        <div className="relative min-h-[280px] bg-gradient-to-br from-[#4f46e5] to-[#7c3aed]" data-node-id="12:346">
          <img alt="" className="absolute inset-0 size-full object-cover mix-blend-luminosity opacity-90" src={imgHero} />
          <div className="absolute left-6 top-6 rounded-full bg-white/20 px-3 py-1 text-[12px] font-medium text-white backdrop-blur">
            New in v2.0
          </div>
        </div>
        <div className="flex flex-col gap-6 p-10" data-node-id="12:360">
          <div className="flex flex-col gap-2">
            <p className="text-[13px] font-semibold uppercase tracking-[1.2px] text-[#4f46e5]">Design to code</p>
            <h1 className="text-[32px] font-bold leading-[38px] text-[#0f172a]">Ship screens straight from Figma</h1>
            <p className="text-[16px] leading-6 text-[#475569]">
              Paste a frame link, review the generated React, and download a project that runs.
            </p>
          </div>
          <div className="flex flex-col gap-4">
            <Feature title="Pixel-faithful layout" body="Spacing, colour and type come from the design." />
            <Feature title="Editable in place" body="Tweak the code and watch the preview update." />
            <Feature title="Ready to run" body="Vite, React and Tailwind wired up for you." />
          </div>
          <div className="flex items-center gap-3">
            <button className="rounded-[12px] bg-[#4f46e5] px-5 py-3 text-[15px] font-semibold text-white hover:bg-[#4338ca]">
              Get started
            </button>
            <button className="rounded-[12px] border border-[#cbd5e1] px-5 py-3 text-[15px] font-semibold text-[#0f172a] hover:bg-[#f8fafc]">
              View docs
            </button>
          </div>
          <div className="flex items-center gap-3 border-t border-[#e2e8f0] pt-5">
            <img alt="" className="size-9 rounded-full" src={imgAvatar} />
            <p className="text-[13px] leading-5 text-[#64748b]">"Cut our hand-off time in half." · Design Systems team</p>
          </div>
        </div>
      </div>
    </div>
  );
}
