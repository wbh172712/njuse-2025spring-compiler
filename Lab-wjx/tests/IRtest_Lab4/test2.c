int g(int x){
    return x+1;
}
int main(){
    int x=g(10);
    while(x<100){
        x=x+1;
        if(x>30){
            while(x>10){
                if(x>20){
                  if(x>21){
                    x=x-5;

                  }
                   x=x-1;
                   continue;
                }
                else if(x>25){
                    x=x+1;
                }
                else break;
            }
            x=x-1;
            return x;
        }
        if(x>10){
            x=x+5;
        }
        else break;
    }
    return 1;
}