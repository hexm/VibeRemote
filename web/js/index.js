const { createApp, ref, onMounted } = Vue;

const app = createApp({
    setup() {
        const userInfo = ref({});

        const checkLogin = () => {
            const token = localStorage.getItem('jwt_token');
            if (token) {
                // In a real app, you'd decode the token to get user info
                // For simplicity, we'll just assume the user is logged in.
                // A more robust approach would be to have a /api/user/me endpoint.
                try {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    userInfo.value = { username: payload.sub };
                } catch (e) {
                    console.error('Invalid token:', e);
                    localStorage.removeItem('jwt_token');
                }
            } else {
                userInfo.value = {};
            }
        };

        const logout = () => {
            localStorage.removeItem('jwt_token');
            userInfo.value = {};
            window.location.href = '/login.html';
        };

        const goToDashboard = () => window.location.href = '/dashboard.html';
        const viewDocumentation = () => alert('Documentation coming soon!');
        const goToAgents = () => window.location.href = '/agents.html';
        const goToTasks = () => window.location.href = '/tasks.html';
        const goToScripts = () => window.location.href = '/scripts.html';

        onMounted(() => {
            checkLogin();
        });

        return {
            userInfo,
            logout,
            goToDashboard,
            viewDocumentation,
            goToAgents,
            goToTasks,
            goToScripts,
        };
    },
});

app.use(ElementPlus);
app.mount('#app');
