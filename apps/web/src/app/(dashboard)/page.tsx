import Image from "next/image";

export default function Home() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center bg-white">
      <main className="flex w-full max-w-3xl flex-col items-center justify-between rounded-2xl border border-zinc-200 bg-white px-10 py-12 shadow-sm sm:items-start">
        <Image
          className="dark:invert"
          src="/next.svg"
          alt="Next.js logo"
          width={100}
          height={20}
          priority
        />
        <div className="flex flex-col items-center gap-6 text-center sm:items-start sm:text-left">
          <h1 className="max-w-xs text-3xl font-semibold leading-10 tracking-tight text-black">
            To get started, edit the page.tsx file.
          </h1>
          <p className="max-w-md text-lg leading-8 text-zinc-600">
            This dashboard shell is protected. Replace this content with your Overview.
          </p>
        </div>
      </main>
    </div>
  );
}
