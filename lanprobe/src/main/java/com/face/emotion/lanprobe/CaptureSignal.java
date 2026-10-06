package com.face.emotion.lanprobe;

/** Pure signal utilities. Upsampling does not recover frequencies absent from the source. */
final class CaptureSignal {
    static short[] resample(short[] input,int from,int to) {
        if(from<=0||to<=0)throw new IllegalArgumentException("Invalid sample rate");
        if(input.length==0)return new short[0];
        if(from==to)return input.clone();
        if(to!=from*2)return convertRate(input,from,to);
        // Windowed sinc half-band interpolation suppresses spectral images above source Nyquist.
        // Boundary taps are renormalized over available samples, never extended with silence.
        final int radius=16;double[] weights=new double[radius*2];
        for(int k=0;k<weights.length;k++){
            double distance=0.5-(k-radius+1);
            weights[k]=Math.sin(Math.PI*distance)/(Math.PI*distance)*(0.5+0.5*Math.cos(Math.PI*distance/radius));
        }
        short[] result=new short[input.length*2];
        for(int i=0;i<input.length;i++){
            result[2*i]=input[i];double value=0,total=0;
            for(int k=0;k<weights.length;k++){int index=i+k-radius+1;if(index>=0&&index<input.length){value+=input[index]*weights[k];total+=weights[k];}}
            result[2*i+1]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(value/total)));
        }
        return result;
    }
    private static short[] convertRate(short[] input,int from,int to){
        int a=from,b=to;while(b!=0){int r=a%b;a=b;b=r;}
        int phases=to/a;
        if(phases>48000)throw new IllegalArgumentException("Rate ratio too large");
        double cutoff=Math.min(1,to/(double)from)*0.94;
        int radius=(int)Math.ceil(24/cutoff),taps=radius*2;
        double[][] weights=new double[phases][taps];
        for(int phase=0;phase<phases;phase++){
            double fraction=((long)phase*from%to)/(double)to;
            for(int k=0;k<taps;k++){
                double d=fraction-(k-radius+1),x=Math.PI*d*cutoff;
                weights[phase][k]=(Math.abs(x)<1e-10?1:Math.sin(x)/x)*cutoff*(Math.abs(d)<radius?0.5+0.5*Math.cos(Math.PI*d/radius):0);
            }
        }
        short[] result=new short[(int)((long)input.length*to/from)];
        for(int i=0;i<result.length;i++){
            int base=(int)((long)i*from/to);double sum=0,gain=0;double[] w=weights[i%phases];
            for(int k=0;k<taps;k++){int index=base+k-radius+1;if(index>=0&&index<input.length){sum+=input[index]*w[k];gain+=w[k];}}
            result[i]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(sum/gain)));
        }
        return result;
    }
    static double[] spectrum(short[] data,int offset,int n) {
        if(n<2||(n&(n-1))!=0||offset<0||offset+n>data.length)throw new IllegalArgumentException("Invalid FFT window");
        double[] re=new double[n],im=new double[n];double gain=0;
        for(int i=0;i<n;i++){double w=0.5-0.5*Math.cos(2*Math.PI*i/(n-1));gain+=w;re[i]=data[offset+i]/32768.0*w;}
        for(int i=1,j=0;i<n;i++){int bit=n>>1;for(; (j&bit)!=0;bit>>=1)j^=bit;j^=bit;if(i<j){double t=re[i];re[i]=re[j];re[j]=t;}}
        for(int len=2;len<=n;len<<=1)for(int base=0;base<n;base+=len)for(int k=0;k<len/2;k++) {
            double angle=-2*Math.PI*k/len,c=Math.cos(angle),s=Math.sin(angle);
            int a=base+k,b=a+len/2;double r=re[b]*c-im[b]*s,v=re[b]*s+im[b]*c;
            re[b]=re[a]-r;im[b]=im[a]-v;re[a]+=r;im[a]+=v;
        }
        double[] db=new double[n/2+1];
        for(int i=0;i<db.length;i++)db[i]=20*Math.log10(Math.max(1e-6,Math.hypot(re[i],im[i])*(i==0||i==n/2?1:2)/gain));
        return db;
    }
}
