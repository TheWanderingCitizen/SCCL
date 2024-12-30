package cn.citizenwiki.config;

import cn.citizenwiki.api.github.GithubConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class JGitConfig {

    public static final CredentialsProvider CREDENTIALS_PROVIDER = new UsernamePasswordCredentialsProvider(GithubConfig.INSTANCE.getForkOwner(), GithubConfig.INSTANCE.getToken());

}
