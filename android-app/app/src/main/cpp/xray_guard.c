#include <signal.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <unistd.h>

/* Keep the child inside the owning Android service's lifetime, including LMKD kills. */
int main(int argc, char **argv) {
    if (argc < 2) return 64;
    pid_t parent = getppid();
    if (parent <= 1 || prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != parent) return 70;
    execv(argv[1], argv + 1);
    return 71;
}
