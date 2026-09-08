function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : null;
}

export async function logout() {
  const token = readCookie("XSRF-TOKEN");
  await fetch("/logout", {
    method: "POST",
    credentials: "include",
    headers: token ? { "X-XSRF-TOKEN": token } : {}
  });
  window.location.href = "/";
}
