import os
import subprocess
import sys
import zipfile

def run_command(command):
    result = subprocess.run(command, shell=True, capture_output=True, text=True)
    return result.stdout.strip(), result.returncode

def main():
    print("--------Begin to Submit--------")

    COURSE = "PTC2022"

    branch_output, return_code = run_command("git rev-parse --abbrev-ref HEAD")
    if return_code != 0:
        print("Error: Failed to get current branch.")
        sys.exit(1)
    MODULE = branch_output.upper()

    WORKSPACE = os.path.basename(os.getcwd())

    FILE = "submit.zip"

    status_output, return_code = run_command("git status --porcelain")
    if return_code != 0:
        print("Error: Failed to check Git status.")
        sys.exit(1)
    if status_output:
        print("Error: Git repository is dirty.")
        print("Commit all your changes before submitting.")
        print("Hint: run 'git status' to show changed files.")
        sys.exit(1)

    ANTLRTMP = "labN"
    BRANCH = branch_output
    NUMBER = "".join(filter(str.isdigit, BRANCH))
    ID = ANTLRTMP.replace("N", NUMBER)

    print(f"In branch: {BRANCH}")
    print(f"Submit to assignment: {ID}")

    os.chdir("..")
    if os.path.exists(FILE):
        os.remove(FILE)

    try:
        with zipfile.ZipFile(FILE, "w") as zipf:
            git_dir = os.path.join(WORKSPACE, ".git")
            if os.path.exists(git_dir):
                for root, dirs, files in os.walk(git_dir):
                    for file in files:
                        file_path = os.path.join(root, file)
                        arcname = os.path.join(WORKSPACE, os.path.relpath(file_path, start=WORKSPACE))
                        zipf.write(file_path, arcname)
                print("\ngenerate submit.zip")
            else:
                print("\nError: .git directory not found!")
                sys.exit(1)
    except Exception as e:
        print(f"\nFail to zip for submit.zip: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
