import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ScamShield · Rug-proof your agent",
  description:
    "The trust layer AI agents call before any swap. Signed token risk scores on Base, paid via x402.",
  openGraph: {
    title: "ScamShield · Rug-proof your agent",
    description: "Signed token risk scores on Base. Paid via x402.",
    type: "website",
  },
  twitter: {
    card: "summary_large_image",
    title: "ScamShield",
    description: "Rug-proof your agent.",
  },
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
