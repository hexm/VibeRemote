const { createApp, ref, onMounted } = Vue;

const app = createApp({
    setup() {
        const userInfo = ref({});

        const checkLogin = () => {
            const token = localStorage.getItem('jwt_token');
            const username = localStorage.getItem('username');
            
            console.log('checkLogin - token:', token ? 'exists' : 'null');
            console.log('checkLogin - username:', username);
            
            if (token && username) {
                userInfo.value = { username: username };
                console.log('userInfo set to:', userInfo.value);
            } else if (token) {
                // Try to decode token as fallback
                try {
                    const payload = JSON.parse(atob(token.split('.')[1]));
                    console.log('Token payload:', payload);
                    if (payload.sub) {
                        userInfo.value = { username: payload.sub };
                        localStorage.setItem('username', payload.sub);
                        console.log('userInfo set from token:', userInfo.value);
                    }
                } catch (e) {
                    console.error('Invalid token:', e);
                    localStorage.removeItem('jwt_token');
                    userInfo.value = {};
                }
            } else {
                console.log('No token found, userInfo cleared');
                userInfo.value = {};
            }
        };

        const logout = () => {
            localStorage.removeItem('jwt_token');
            localStorage.removeItem('username');
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
