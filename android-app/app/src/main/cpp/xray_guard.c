#include <signal.h>
#include <fcntl.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

/* Keep the child inside the owning Android service's lifetime, including LMKD kills. */
int main(int argc, char **argv) {
    if (argc < 2) return 64;
    pid_t parent = getppid();
    if (parent <= 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) return 70;
    /* Upstream errors may contain credentials; never leave them in logcat or pipes. */
    int output = open("/dev/null", O_WRONLY | O_CLOEXEC);
    if (output < 0 || dup2(output, STDOUT_FILENO) < 0 || dup2(output, STDERR_FILENO) < 0) return 70;
    if (output > STDERR_FILENO) close(output);
    execv(argv[1], argv + 1);
    return 71;
}
