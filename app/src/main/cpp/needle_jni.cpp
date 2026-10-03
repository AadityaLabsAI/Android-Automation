#include <jni.h>
#include "needle.h"
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <string>
#include <vector>

namespace {
void* g_model=nullptr;
std::string str(JNIEnv* e,jstring s){
    if(!s)return{};
    const char* p=e->GetStringUTFChars(s,nullptr);
    std::string r=p?p:"";
    if(p)e->ReleaseStringUTFChars(s,p);
    return r;
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeLoadModel(JNIEnv* e,jclass,jstring path){
    if(g_model)return 0;
    std::string p=str(e,path);
    int fd=open(p.c_str(),O_RDONLY|O_CLOEXEC);
    if(fd<0)return -errno;
    struct stat st{};
    if(fstat(fd,&st)!=0){int x=-errno;close(fd);return x;}
    if(st.st_size<=0){close(fd);return -22;}
    void* m=mmap(nullptr,(size_t)st.st_size,PROT_READ,MAP_PRIVATE,fd,0);
    close(fd);
    if(m==MAP_FAILED)return -errno;
    int rc=needle_load((const unsigned char*)m,(unsigned long long)st.st_size);
    if(rc<0){munmap(m,(size_t)st.st_size);return rc;}
    g_model=m;
    return rc;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeInit(JNIEnv* e,jclass,jstring sys,jstring tools,jstring idx){
    std::string s=str(e,sys),t=str(e,tools),i=str(e,idx);
    return needle_init(s.c_str(),t.c_str(),i.empty()?nullptr:i.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeComplete(JNIEnv* e,jclass,jstring input,jint maxTokens){
    std::string q=str(e,input);
    std::vector<char> out(65536,0);
    int rc=needle_complete(q.c_str(),(int)maxTokens,out.data(),(int)out.size());
    if(rc<0){
        std::string err=std::string("{\\"type\\":\\"error\\",\\"code\\":")+std::to_string(rc)+"}";
        return e->NewStringUTF(err.c_str());
    }
    return e->NewStringUTF(out.data());
}

extern "C" JNIEXPORT void JNICALL
Java_com_aadityalabs_needle2_NativeNeedle_nativeReset(JNIEnv*,jclass){needle_reset();}
