package is2.tag3;



import is2.data.FV;
import is2.data.Instances;
import is2.data.PipeGen;

import is2.data.SentenceData09;
import is2.io.CONLLReader09;
import is2.io.CONLLWriter09;
import is2.util.DB;
import is2.util.OptionsSuper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class Tagger {

	public static Pipe pipe;
	public static ParametersFloat params;
	private OptionsSuper options;

	
	/**
	 * Initialize 
	 * @param options
	 */
	public Tagger (Options options) {
	
		this.options=options;
//		pipe = new Pipe(options);
		Long2Int li = new Long2Int();

//		params = new ParametersFloat(0);  
		
		// load the model
		try {
			init(options, li);
		} catch (Exception e) {
			e.printStackTrace();
		}
				
	}
	
	public static void main (String[] args) throws FileNotFoundException, Exception
	{

		long start = System.currentTimeMillis();
		Options options = new Options(args);
		Long2Int li = new Long2Int();
		if (options.train) {

			Pipe pipe =  new Pipe (options,li);
			
			Instances is = new Instances();
			pipe.createInstances(options.trainfile,options.trainforest,is);
			
			ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(options.modelName)));
			zos.putNextEntry(new ZipEntry("data")); 
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(zos));


			ParametersFloat params = new ParametersFloat(li.size());
			
			train(options, pipe,params,is);

			MFO.writeData(dos);
			MFO.clearData();

			DB.println("number of parameters "+params.parameters.length);
			dos.flush();

			params.write(dos);

			dos.flush();
			dos.close();
						
			System.out.print("done.");
		}

		if (options.test) {

			init(options,li);

			out(options,pipe, params);
		}

		System.out.println();

		if (options.eval) {
			System.out.println("\nEVALUATION PERFORMANCE:");
			Evaluator.evaluate(options.goldfile, options.outfile,options.format);
		}
		long end = System.currentTimeMillis();
		System.out.println("used time "+((float)((end-start)/100)/10));
	}


	public static void init(Options options, Long2Int li) throws IOException,FileNotFoundException {
		pipe = new Pipe(options, li);
		params = new ParametersFloat(li.size());

	
		// load the model
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(options.modelName)));
		zis.getNextEntry();
		DataInputStream dis = new DataInputStream(new BufferedInputStream(zis));
		MFO.read(dis);
		pipe.initValues();
		pipe.initFeatures();
		
		params.read(dis);
		dis.close();

		DB.println("num params "+params.parameters.length);
		
		Pipe.types = new String[MFO.getFeatureCounter().get(Pipe.POS)];
		for(Entry<String,Integer> e : MFO.getFeatureSet().get(Pipe.POS).entrySet()) 
			Pipe.types[e.getValue()] = e.getKey();
		
		System.out.println("Loading data finnished. ");
		
		pipe.mf.stop();
	}

	
	

	/**
	 * Do the training
	 * @param instanceLengths
	 * @param options
	 * @param pipe
	 * @param params
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ClassNotFoundException
	 */
	static public void train(Options options, Pipe pipe, ParametersFloat params, Instances is) 
	throws IOException, InterruptedException, ClassNotFoundException {


		int i = 0;
		int del=0; 
		
		for(i = 0; i < options.numIters; i++) {

			long start = System.currentTimeMillis();
		
			BufferedInputStream bos = new BufferedInputStream(new FileInputStream(options.trainforest));
			DataInputStream dos = new DataInputStream(bos);

			int numInstances = is.size();
					
			long last= System.currentTimeMillis();
			FV pred = new FV();
			FV gold = new FV();
			int correct =0,count=0;
			
			for(int n = 0; n < numInstances; n++) {
	
				if((n+1) % 500 == 0) del= PipeGen.outValueErr(n+1, (count-correct),(float)correct/(float)count,del,last);
				
				int length = is.length(n);
			    pipe.readInstance(dos, length,params,options,null);				
			    
				SentenceData09 instance = pipe.getInstance();
			   
				Object d[][] = new Object[length][2];

				double upd = (options.numIters*numInstances - (numInstances*i+(n+1))+ 1);


			
				
				for(int w1 = 0; w1 < length; w1++) {
						
					int bestType = pipe.fillFeatureVectorsOne(instance, params, w1, d, is, n, is.gpos[n]);
					
					count++;

					// 
					double err =  ((!instance.ppos[w1].equals(instance.gpos[w1]) && !instance.ppos[w1].equals("_") ) 
							|| instance.gpos[w1].equals(d[w1][1])) ?0.0: 1.0;

					// bestType == gpos == pposs 
					if (bestType == is.gpos[n][w1] ) {
						correct++;
						continue; 
					}
					
					pred.clear();
					pipe.addCoreFeatures(instance,w1,Pipe.types[bestType],is.gpos[n], is.forms[n],is.lemmas[n],pred);

					gold.clear();
					pipe.addCoreFeatures(instance,w1,Pipe.types[is.gpos[n][w1]],is.gpos[n], is.forms[n],is.lemmas[n],gold);
					
					params.update(pred,gold, upd, err);
				}

			}


		//	System.out.print(numInstances);
 
			

			long end = System.currentTimeMillis();
			//System.out.println("Training iter took: " + (end-start));
			String info = "time "+(end-start);
			del= PipeGen.outValueErr(numInstances, (count-correct),(float)correct/(float)count,del,last,0,info);
			
			System.out.println("|Time:"+(end-start)+"]");			
		}

		params.averageParams(i*is.size());

	}

	
	static public void outputParses (Options options) throws Exception  {
		out ( options,  pipe,  params);
	}

	/**
	 * Tag a sentence
	 * @param options
	 * @param pipe
	 * @param params
	 * @throws IOException
	 */
	static public void out (Options options, Pipe pipe, ParametersFloat params) 
	throws Exception {

		
		long start = System.currentTimeMillis();

		CONLLReader09 depReader = new CONLLReader09(options.testfile);
		CONLLWriter09 depWriter = new CONLLWriter09(options.outfile);


		System.out.print("Processing Sentence: ");
		pipe.initValues();

		int cnt = 0;
		int del=0;
		while(true) {

			Instances is = new Instances();
			is.init(1, pipe.mf);
			SentenceData09 instance = depReader.getNext(is);
			if (instance == null || instance.forms == null)	 break;
			
			cnt++;

			String[] forms = instance.forms;

			instance.ppos = new String[instance.gpos.length];

			int length = instance.ppos.length;

			short[] pos = new short[instance.gpos.length];
			
			Object[][] d = new Object[length][2];
			for(int j = 0; j < length; j++) {

				short bestType = (short)pipe.fillFeatureVectorsOne(instance,params, j, d,is,0,pos);
			 	pos[j] = bestType;
				instance.ppos[j]= (String)d[j][1];
			}

			for(int j = 0; j < length; j++) {

				pipe.fillFeatureVectorsOne(instance,params, j, d,is,0,pos);
				instance.ppos[j]= (String)d[j][1];
			}

		

			Arrays.toString(forms);
			
			String[] formsNoRoot = new String[forms.length-1];
			String[] posNoRoot = new String[formsNoRoot.length];
			String[] lemmas = new String[formsNoRoot.length];

			String[] org_lemmas = new String[formsNoRoot.length];

			String[] of = new String[formsNoRoot.length];
			String[] pf = new String[formsNoRoot.length];
			
			String[] pposs = new String[formsNoRoot.length];
			String[] labels = new String[formsNoRoot.length];
			String[] fillp = new String[formsNoRoot.length];
			                             
			int[] heads = new int[formsNoRoot.length];

			for(int j = 0; j < formsNoRoot.length; j++) {
				formsNoRoot[j] = forms[j+1];
				posNoRoot[j] = instance.gpos[j+1];


		//		posNoRoot[j] = (String)d[j+1][1];
				pposs[j] = instance.ppos[j+1];

				labels[j] = instance.labels[j+1];// Pipe.types[is.deprels[cnt-1][j+1]];
				heads[j] = instance.heads[j+1];
				//System.out.println("head "+heads[j]);
				lemmas[j] = instance.lemmas[j+1];
				org_lemmas[j] = instance.org_lemmas[j+1];

//				labels[j] = pipe.types[d.types[j+1]];
//				heads[j] = d.heads[j+1];
				lemmas[j] = instance.lemmas[j+1];

				if (instance.org_lemmas!=null) org_lemmas[j] = instance.org_lemmas[j+1];
				if (instance.ofeats!=null)  of[j] = instance.ofeats[j+1];
				if (instance.pfeats!=null)	pf[j] = instance.pfeats[j+1];

				if (instance.fillp!=null) fillp[j] = instance.fillp[j+1];
			}

			
			SentenceData09 i09 = new SentenceData09(formsNoRoot, lemmas, org_lemmas,posNoRoot, pposs, labels, heads,fillp,of, pf);

			
			i09.fillp = fillp;
			i09.sem = instance.sem;
			i09.semposition = instance.semposition;
			
			if (instance.semposition!=null)
			for (int k= 0;k< instance.semposition.length;k++) {
				i09.semposition[k]=instance.semposition[k]-1;
			}
			
			
			i09.arg = instance.arg;

			
			i09.argposition = instance.argposition;

			if (i09.argposition!=null)
			for (int p= 0;p< instance.argposition.length;p++) {
				if (i09.argposition[p]!=null)
				for(int a=0;a<instance.argposition[p].length;a++)
				i09.argposition[p][a]=instance.argposition[p][a]-1;
			}


			depWriter.write(i09);
			
			del=PipeGen.outValue(cnt, del);

		}
		if (null != pipe.depWriter) {
			pipe.depWriter.finishWriting();
		}
		depWriter.finishWriting();

		long end = System.currentTimeMillis();
		System.out.println("Used time " + (end-start));

	}
	
	/**
	 * Tag a sentence
	 * @param options
	 * @param pipe
	 * @param params
	 * @throws IOException
	 */
	public String[] tag (String[] words, String[] lemmas)  {

		String[] pposs = new String[words.length];

		try {
			pipe.initValues();

			int length = words.length+1;

			
			Instances is = new Instances();
			is.init(1, pipe.mf);
			is.createInstance09(length);

			SentenceData09 instance = new SentenceData09();
			instance.forms = new String[length];
			instance.forms[0]=is2.io.CONLLReader09.ROOT;
			
			instance.lemmas = new String[length];
			instance.lemmas[0]=is2.io.CONLLReader09.ROOT_LEMMA;

			for(int j = 0; j < words.length; j++) {
				instance.forms[j+1]=words[j];
				instance.lemmas[j+1]=lemmas[j];
			}
			
			for(int j = 0; j < length; j++) {
				is.setForm(0, j, instance.forms[j]);
				is.setLemma(0, j, instance.lemmas[j]);
			}
			
		
			instance.ppos = new String[length];


			short[] pos = new short[length];
			
			
			
			
			Object[][] d = new Object[length][2];
			for(int j = 0; j < length; j++) {

				short bestType = (short)pipe.fillFeatureVectorsOne(instance,params, j, d,is,0,pos);
			 	pos[j] = bestType;
				instance.ppos[j]= (String)d[j][1];
			}

			for(int j = 0; j < length; j++) {

				pipe.fillFeatureVectorsOne(instance,params, j, d,is,0,pos);
				instance.ppos[j]= (String)d[j][1];
			}

		

			
		//	String[] pposs = new String[words.length];

			for(int j = 0; j < words.length; j++) {
					pposs[j] = instance.ppos[j+1];	
//					System.out.print(pposs[j]+" ");
			}
//			System.out.println();
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return pposs;
			

	}
	
	
	

}