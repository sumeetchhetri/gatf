package com.gatf.simulators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import com.gatf.executor.core.AcceptanceTestContext.SimulatorInt;
import com.icegreen.greenmail.imap.commands.IdRange;
import com.icegreen.greenmail.store.MailFolder;
import com.icegreen.greenmail.store.StoredMessage;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.Address;
import jakarta.mail.Flags;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

public class EmailGateway implements SimulatorInt {
	private GreenMail greenMail;
	private LinkedBlockingQueue<MimeMessage> requests = new LinkedBlockingQueue<>();
	
	public void start(Object[] args) {
		String login = args[0].toString();
		String password = args[1].toString();
		boolean isSecure = (Boolean)args[2];
		int smtpPort = (Integer)args[3];
		int imapPort = (Integer)args[4];
		greenMail = new GreenMail(new ServerSetup[] {
				new ServerSetup(smtpPort, null, isSecure?ServerSetup.PROTOCOL_SMTPS:ServerSetup.PROTOCOL_SMTP),
				new ServerSetup(imapPort, null, isSecure?ServerSetup.PROTOCOL_IMAPS:ServerSetup.PROTOCOL_IMAP)
		});
		greenMail.setUser(login, password);
		greenMail.start();
	}
	
	public void stop() {
		if(greenMail!=null) {
			greenMail.stop();
		}
	}
	
	public boolean isEventReceived(Object[] args) {
		String from = args[0].toString();
		String to = args[1].toString();
		String subject = args[2].toString();
		String content = args[3].toString();
		long timeout = Long.parseLong(args[4].toString());
		
		greenMail.waitForIncomingEmail(timeout, 1);
		try {
			Collection<MailFolder> boxes = greenMail.getManagers().getImapHostManager().getStore().listMailboxes("*");
			if(boxes.size()>0) {
				for (MailFolder boxe : boxes) {
					List<IdRange> range = new ArrayList<>();
					if(boxe.getMessages().size()>0) {
						for (StoredMessage sm : boxe.getMessages()) {
							sm.getMimeMessage().setFlag(Flags.Flag.DELETED, true);
							range.add(new IdRange(sm.getUid()));
							requests.add(sm.getMimeMessage());
						}
						boxe.expunge(range.toArray(new IdRange[range.size()]));
					}
	            }
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if(requests.size()>0) {
			Iterator<MimeMessage> it = requests.iterator();
	        while (it.hasNext()) {
	        	MimeMessage mimeMessage = it.next();
				try {
					if(mimeMessage.getFrom()[0].toString().equalsIgnoreCase(from)) {
						boolean fl = false;
						for (Address pto : mimeMessage.getRecipients(Message.RecipientType.TO)) {
							fl |= pto.toString().equalsIgnoreCase(to);
						}
						if(StringUtils.isNotBlank(subject)) {
							try {
								Pattern p = Pattern.compile(subject);
								fl &= p.matcher(mimeMessage.getSubject()).matches();
							} catch (Exception e) {
								fl &= mimeMessage.getSubject().equalsIgnoreCase(subject);
							}
						}
						if(StringUtils.isNotBlank(content)) {
							String mcont = GreenMailUtil.getBody(mimeMessage);
							if(StringUtils.isNotBlank(mcont)) {
								try {
									Pattern p = Pattern.compile(content);
									fl &= p.matcher(mcont).matches();
								} catch (Exception e) {
									fl &= mcont.equalsIgnoreCase(content);
								}
							}
						}
						if(fl) {
							it.remove();
							return true;
						}
					}
	
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return false;
	}
}
