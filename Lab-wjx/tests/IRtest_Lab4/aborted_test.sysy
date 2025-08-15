int x[2003];
int y;
void p(){
    y=y+1;
    return;
}
int f(int x,int t){
    p();
    return x+t+y;
}
int g(int x,int y,int z){
    return f(x,z);
}
int main(){
    int t=100;
    int x=g(2,4,t);
    /*
    int y=100;
    int z=y+100;
    if(x||x%0||x/0&&x){
        int y=100;
        {
            int x=5;
            while(x>0){
                x=x+5;
                if(x<100){
                    y=y+1;
                    continue;
                }
                else if(x<50){
                    y=y+1;
                    continue;
                }
                break;
            }
        }
        x=x+y+5;
    }
    */
    return x;
}