const nav = document.createElement('nav');
nav.className = 'navbar navbar-expand-sm navbar-dark';
nav.innerHTML = `
    <div class="container" style="max-width:700px">
        <a class="navbar-brand fw-bold" href="/home.html">Itadaki</a>
        <div class="d-flex align-items-center gap-3 ms-auto">
            <a href="/home.html"    class="nav-link text-white">Accueil</a>
            <a href="/history.html" class="nav-link text-white">Historique</a>
            <a href="/stats.html"   class="nav-link text-white">Statistiques</a>
            <div class="vr bg-white opacity-25 mx-1"></div>
            <span id="navUsername" class="badge rounded-pill text-success fw-semibold" style="background:#fff;font-size:.8rem"></span>
            <button class="btn btn-outline-light btn-sm" id="logoutBtn">Déconnexion</button>
        </div>
    </div>
`;
document.body.prepend(nav);

const meRes = await fetch('/api/auth/me', { credentials: 'include' });
if (!meRes.ok) { globalThis.location.href = '/login.html'; }
else {
    document.getElementById('navUsername').textContent = await meRes.text();
    document.getElementById('logoutBtn').addEventListener('click', async () => {
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
        globalThis.location.href = '/login.html';
    });
}
