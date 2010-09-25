package se.lth.cs.srl.languages;

import is2.lemmatizer.Lemmatizer;
import is2.tag3.Tagger;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import se.lth.cs.srl.corpus.Predicate;
import se.lth.cs.srl.corpus.Sentence;
import se.lth.cs.srl.corpus.Word;
import se.lth.cs.srl.options.FullPipelineOptions;
import se.lth.cs.srl.preprocessor.Preprocessor;
import se.lth.cs.srl.preprocessor.tokenization.OpenNLPToolsTokenizerWrapper;
import se.lth.cs.srl.preprocessor.tokenization.Tokenizer;
import se.lth.cs.srl.util.BohnetHelper;

public class German extends Language {

	private static final Pattern BAR_PATTERN=Pattern.compile("\\|");
	
	@Override
	public Pattern getFeatSplitPattern() {
		return BAR_PATTERN;
	}

	@Override
	public String getDefaultSense(String lemma) {
		return lemma+".1";
	}

	@Override
	public String getCoreArgumentLabelSequence(Predicate pred,Map<Word, String> proposition) {
		Sentence sen=pred.getMySentence();
		StringBuilder ret=new StringBuilder();
		for(int i=1,size=sen.size();i<size;++i){
			Word word=sen.get(i);
			if(pred==word){
				ret.append(" "+pred.getSense()+"/");
				ret.append(isPassiveVoice(pred) ? "P" : "A");
			}
			if(proposition.containsKey(word)){
				ret.append(" "+proposition.get(word));
			}
		}
		
		
		return ret.toString();
	}

	private boolean isPassiveVoice(Predicate pred){
		Word head=pred.getHead();
		return !head.isBOS() && pred.getPOS().equals("VVPP") && head.getLemma().equals("werden");
	}

	@Override
	public L getL() {
		return L.ger;
	}

	@Override
	public String getLexiconURL(Predicate pred) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Preprocessor getPreprocessor(FullPipelineOptions options) throws IOException {
		Tokenizer tokenizer=new OpenNLPToolsTokenizerWrapper(new opennlp.tools.lang.german.Tokenizer(options.tokenizer.toString()));
		Lemmatizer lemmatizer=BohnetHelper.getLemmatizer(options.lemmatizer);
		Tagger tagger=BohnetHelper.getTagger(options.tagger);
		is2.mtag.Main mtagger=BohnetHelper.getMTagger(options.morph);
		Preprocessor pp=new Preprocessor(tokenizer, lemmatizer, tagger, mtagger);
		return pp;
	}
	
}