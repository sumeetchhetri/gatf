package org.springframework.web.bind.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({java.lang.annotation.ElementType.METHOD, java.lang.annotation.ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Mapping
public @interface RequestMapping
{
  public abstract String[] value();

  public abstract RequestMethod[] method();

  public abstract String[] params();

  public abstract String[] headers();

  public abstract String[] consumes();

  public abstract String[] produces();
}

/* Location:           C:\Users\sumeetc\.m2\repository\org\springframework\spring-web\4.0.0.RELEASE\spring-web-4.0.0.RELEASE.jar
 * Qualified Name:     org.springframework.web.bind.annotation.RequestMapping
 * Java Class Version: 6 (50.0)
 * JD-Core Version:    0.5.3
 */