package io.operon.runner.model;

public class ModuleDefinition {
	
	private String body;
	private String namespace;
	private String filePath = "./";
	
	public void setBody(String b) {
		this.body = b;
	}
	
	public String getBody() {
		return this.body;
	}
	
	public void setNamespace(String ns) {
		this.namespace = ns;
	}
	
	public String getNamespace() {
		return this.namespace;
	}
	
	public void setFilePath(String fp) {
		this.filePath = fp;
	}
	
	public String getFilePath() {
		return this.filePath;
	}
}