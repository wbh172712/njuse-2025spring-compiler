int x = 100, y = 2, z = 3;
void g(){
    x=x+1;
}
int f(int x){
    if(x==0) return 0;
    x=f(x-1)+10;
    return x;
}
int main() {
    while(1){
    x=f(200);
    while(x){
        g();
        if(x>=200) break;
    }
    if(x>=100){
        if(x<10) return 0;
        else if(x>200) return 2;
        else if(x>0x100) return 3;
        x=x+1;
        while(x<200){
            x=x+1;
            if(x<125) continue;
            g();
            x=x+2;
            continue;
            break;
        }
    }
    x=5000;
    if(x>=125){
        if(x<2030)
        {
            while(x>100){
                if(x) x=x-1;
                else x=x-2;
             }
             if(x>100) return x;
        }
        else if(x<5000){
            x=5000+0x10;
        }
        else if(x<231){
            return x+1;
        }
        if(x>10){
            x=x-10;
        }
    }
    x-=100;
    break;
    }
    return x;
}