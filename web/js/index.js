const { createApp, ref, onMounted } = Vue;

const app = createApp({
    setup() {

        const goToDashboard = () => window.location.href = '/dashboard.html';
        const viewDocumentation = () => alert('Documentation coming soon!');
        const goToAgents = () => window.location.href = '/agents.html';
        const goToTasks = () => window.location.href = '/tasks.html';
        const goToScripts = () => window.location.href = '/scripts.html';

        onMounted(() => {
            console.log('Component mounted');
        });

        return {
            goToDashboard,
            viewDocumentation,
            goToAgents,
            goToTasks,
            goToScripts,
        };
    }
});

app.use(ElementPlus);
app.mount('#app');
