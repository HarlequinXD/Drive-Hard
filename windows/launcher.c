#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <shellapi.h>
#include <wchar.h>
int WINAPI wWinMain(HINSTANCE h,HINSTANCE p,PWSTR cmd,int show) {
    wchar_t path[MAX_PATH], *slash;
    (void)h; (void)p; (void)cmd; (void)show;
    if (!GetModuleFileNameW(NULL,path,MAX_PATH)) return 1;
    slash=wcsrchr(path,L'\\');
    if (!slash) return 1;
    *slash=L'\0';
    if (!SetCurrentDirectoryW(path)) return 1;
    HINSTANCE result=ShellExecuteW(NULL,L"open",L"javaw.exe",L"-jar \"DriveHard.jar\"",path,SW_SHOWNORMAL);
    if ((INT_PTR)result<=32) {
        MessageBoxW(NULL,L"Drive Hard requires Java 17 or newer. Install Java 17, then launch DriveHard.exe again.",L"Drive Hard - Java Required",MB_OK|MB_ICONERROR);
        return 2;
    }
    return 0;
}
